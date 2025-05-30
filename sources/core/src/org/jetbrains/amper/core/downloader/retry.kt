/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.core.downloader

import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import kotlinx.coroutines.delay
import org.jetbrains.amper.core.telemetry.spanBuilder
import org.jetbrains.amper.telemetry.use
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.math.min

// initially from intellij:community/platform/build-scripts/downloader/src/retry.kt
suspend fun <T> suspendingRetryWithExponentialBackOff(
    attempts: Int = 5,
    initialDelayMs: Long = TimeUnit.SECONDS.toMillis(5),
    backOffLimitMs: Long = TimeUnit.MINUTES.toMillis(3),
    backOffFactor: Int = 2, backOffJitter: Double = 0.1,
    onException: suspend (attempt: Int, e: Exception) -> Unit = { attempt, e -> defaultExceptionConsumer(attempt, e) },
    action: suspend (attempt: Int) -> T
): T {
    val random = Random()
    var effectiveDelay = initialDelayMs
    val exceptions = mutableListOf<Exception>()
    for (attempt in 1..attempts) try {
        return action(attempt)
    }
    catch (e: Exception) {
        onException(attempt, e)
        if (e is NoMoreRetriesException) {
            throw Exception("Attempt $attempt failed, stopping retries", e).apply {
                exceptions.forEach(this::addSuppressed)
            }
        }
        exceptions += if (effectiveDelay > 0) {
            Exception("Attempt $attempt failed with '${e.message}', retrying in ${effectiveDelay}ms", e)
        } else e
        if (attempt == attempts) {
            throw Exception("$attempts attempts failed, see suppressed exceptions for details").apply {
                exceptions.forEach(this::addSuppressed)
            }
        }
        if (effectiveDelay > 0) {
            delay(effectiveDelay)
        }
        effectiveDelay = nextDelay(
            random, previousDelay = effectiveDelay,
            backOffLimitMs = backOffLimitMs,
            backOffFactor = backOffFactor,
            backOffJitter = backOffJitter,
            exceptions = exceptions
        )
    }
    error("Should never be reached")
}

class NoMoreRetriesException(message: String, cause: Throwable): Exception(message, cause)

private fun nextDelay(
    random: Random,
    previousDelay: Long,
    backOffLimitMs: Long,
    backOffFactor: Int,
    backOffJitter: Double,
    exceptions: List<Exception>
): Long {
    val nextDelay = min(previousDelay * backOffFactor, backOffLimitMs) + (random.nextGaussian() * previousDelay * backOffJitter).toLong()
    if (nextDelay > backOffLimitMs) {
        throw Exception("Back off limit ${backOffLimitMs}ms exceeded, see suppressed exceptions for details").apply {
            exceptions.forEach(this::addSuppressed)
        }
    }
    return nextDelay
}

private suspend fun defaultExceptionConsumer(attempt: Int, e: Exception) {
    spanBuilder("Retrying action with exponential back off").use { span ->
        span.addEvent("Attempt failed", Attributes.of(
            AttributeKey.longKey("attemptNumber"), attempt.toLong(),
            AttributeKey.stringKey("error"), e.toString()
        ))
    }
}
