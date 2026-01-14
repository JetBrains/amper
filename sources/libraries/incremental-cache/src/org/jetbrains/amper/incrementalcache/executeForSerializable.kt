/*
 * Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.incrementalcache

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import org.jetbrains.amper.telemetry.use
import org.slf4j.LoggerFactory
import java.nio.file.Path
import kotlin.time.Instant

private val logger = LoggerFactory.getLogger("executeForSerializable")

private const val SERIALIZED_OUTPUT_KEY = "serializedOutput"

/**
 * Executes the given [block] and returns the new serializable output value, or immediately returns a deserialized value
 * from the incremental cache for the given [key].
 *
 * This is exactly equivalent to [IncrementalCache.execute], but with automatic serialization and deserialization of
 * the cached value.
 *
 * The serialization is done using the given `kotlinx.serialization`'s [Json].
 *
 * ### Caching
 *
 * The previous result for the same [key] is immediately returned without executing [block] if all the following
 * conditions are met:
 *  * the [inputValues] map has not changed
 *  * the given set of [inputFiles] paths has not changed
 *  * the [inputFiles] themselves have not changed (in terms of size, modification time, and permissions)
 *  * the version of the code that produced the cached result is the same as the current version
 *
 * Note: the output value from previous executions doesn't affect caching because the values only exist in the state
 * file itself, so we have nothing to compare that to.
 * In short, there is no concept of "change" that we could track (unlike output files).
 *
 * ### Concurrency
 *
 * The given [block] is always executed under double-locking based on the given [key], which means that 2 calls with
 * the same [key] cannot be executed at the same time by multiple threads or multiple processes.
 * If one call needs to re-run [block] because the cache is invalid, later calls with the same ID will suspend
 * until the first call completes and then resume and use the cache immediately (if possible).
 */
suspend inline fun <reified S : Any> IncrementalCache.executeForSerializable(
    key: String,
    inputValues: Map<String, String>,
    inputFiles: List<Path>,
    json: Json = Json,
    forceRecalculation: Boolean = false,
    crossinline block: suspend () -> S,
): S = execute(
    key = key,
    inputValues = inputValues,
    inputFiles = inputFiles,
    serializer = serializer<S>(),
    json = json,
    forceRecalculation = forceRecalculation,
) {
    ResultWithSerializable(block())
}.outputValue

/**
 * Executes the given [block] and returns the new serializable output value, or immediately returns a deserialized value
 * from the incremental cache for the given [key].
 *
 * This is exactly equivalent to the regular [IncrementalCache.execute] function, but with automatic serialization and
 * deserialization of the cached value.
 *
 * The serialization is done using the given `kotlinx.serialization`'s [Json].
 *
 * ### Caching
 *
 * The previous result for the same [key] is immediately returned without executing [block] if all the following
 * conditions are met:
 *  * the [inputValues] map has not changed
 *  * the given set of [inputFiles] paths has not changed
 *  * the [inputFiles] themselves have not changed (in terms of size, modification time, and permissions)
 *  * the output files from the latest execution have not changed (in terms of size, modification time,
 *    and permissions)
 *  * the version of the code that produced the cached result is the same as the current version
 *
 * Note: the output _value_ from previous executions doesn't affect caching because the values only exist in the state
 * file itself, so we have nothing to compare that to.
 * In short, there is no concept of "change" that we could track (unlike output files).
 *
 * ### Concurrency
 *
 * The given [block] is always executed under double-locking based on the given [key], which means that 2 calls with
 * the same [key] cannot be executed at the same time by multiple threads or multiple processes.
 * If one call needs to re-run [block] because the cache is invalid, later calls with the same ID will suspend
 * until the first call completes and then resume and use the cache immediately (if possible).
 */
suspend fun <S : Any> IncrementalCache.execute(
    key: String,
    inputValues: Map<String, String>,
    inputFiles: List<Path>,
    serializer: KSerializer<S>,
    json: Json = Json,
    forceRecalculation: Boolean = false,
    block: suspend () -> ResultWithSerializable<S>,
): ResultWithSerializable<S> {
    var outputValue: S? = null
    // Ensure we have a serialized result (either already cached or created on the spot to reuse later)
    val result = execute(
        key = key,
        inputValues = inputValues,
        inputFiles = inputFiles,
        forceRecalculation = forceRecalculation,
    ) {
        val result = tracer.spanBuilder("Compute serializable value").use {
            block()
        }
        outputValue = result.outputValue

        val serializedValue = tracer.spanBuilder("Serialize for cache").use {
            json.encodeToString(serializer, result.outputValue)
        }
        IncrementalCache.ExecutionResult(
            outputFiles = result.outputFiles,
            outputValues = mapOf(SERIALIZED_OUTPUT_KEY to serializedValue),
            expirationTime = result.expirationTime,
        )
    }
    // shortcut: avoid deserialization if we just computed the in-memory value
    if (outputValue != null) {
        return ResultWithSerializable(
            outputValue = outputValue,
            outputFiles = result.outputFiles,
            expirationTime = result.expirationTime,
        )
    }

    val serializedValue = result.outputValues.getValue(SERIALIZED_OUTPUT_KEY)
    val deserializedValue = try {
        tracer.spanBuilder("Deserialize cached value").use {
            json.decodeFromString(serializer, serializedValue)
        }
    } catch (e: SerializationException) {
        // The cached value cannot be deserialized, which means it's not in sync with the code.
        // This shouldn't happen with a correct code identifier, but in case of misuse we can recover.
        // That's why we repeat the call, but this time we force the recalculation of the result.
        logger.error("Failed to deserialize cached value for key '$key'. Possible cache corruption. " +
                "The cache will be discarded.", e)
        return execute(
            key = key,
            inputValues = inputValues,
            inputFiles = inputFiles,
            serializer = serializer,
            json = json,
            forceRecalculation = true,
        ) {
            block()
        }
    }

    return ResultWithSerializable(
        outputValue = deserializedValue,
        outputFiles = result.outputFiles,
        expirationTime = result.expirationTime,
    )
}

data class ResultWithSerializable<S>(
    val outputValue: S,
    val outputFiles: List<Path> = emptyList(),
    val expirationTime: Instant? = null,
)