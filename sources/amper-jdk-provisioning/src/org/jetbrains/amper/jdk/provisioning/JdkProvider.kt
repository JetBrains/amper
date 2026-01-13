/*
 * Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.jdk.provisioning

import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.trace.Span
import kotlinx.serialization.json.Json
import org.jetbrains.amper.concurrency.AsyncConcurrentMap
import org.jetbrains.amper.core.AmperUserCacheRoot
import org.jetbrains.amper.core.UsedInIdePlugin
import org.jetbrains.amper.frontend.schema.JdkSelectionMode
import org.jetbrains.amper.frontend.schema.JdkSettings
import org.jetbrains.amper.incrementalcache.IncrementalCache
import org.jetbrains.amper.problems.reporting.ProblemReporter
import org.jetbrains.amper.telemetry.setAttribute
import org.jetbrains.amper.telemetry.setListAttribute
import org.jetbrains.amper.telemetry.use
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import kotlin.time.Clock

internal val jdkProviderLogger: Logger = LoggerFactory.getLogger(JdkProvider::class.java)

class JdkProvider(
    userCacheRoot: AmperUserCacheRoot,
    private val openTelemetry: OpenTelemetry = OpenTelemetry.noop(),
    private val incrementalCache: IncrementalCache,
) {
    private val javaHomeInfoProvider = JavaHomeInfoProvider(openTelemetry)
    private val jdkProvisioner = JdkProvisioner(openTelemetry, userCacheRoot)

    private val provisioningCache = AsyncConcurrentMap<JdkProvisioningCriteria, JdkResult>()

    /**
     * Finds or provisions a JDK matching the given [jdkSettings].
     *
     * Potential global errors about `JAVA_HOME` are reported via the given [invalidJavaHomeReporter], only once per
     * instance of [JdkProvider].
     * Other failures to provide a matching JDK are reported via the [JdkResult.Failure] type.
     */
    @UsedInIdePlugin
    context(invalidJavaHomeReporter: ProblemReporter)
    suspend fun getJdk(jdkSettings: JdkSettings): JdkResult = getJdk(
        criteria = JdkProvisioningCriteria(
            majorVersion = jdkSettings.version,
            distributions = jdkSettings.distributions?.map { it.value },
            acknowledgedLicenses = jdkSettings.acknowledgedLicenses,
        ),
        selectionMode = jdkSettings.selectionMode
    )

    /**
     * Finds or provisions a JDK matching the given [criteria].
     *
     * Potential global errors about `JAVA_HOME` are reported via the given [invalidJavaHomeReporter], only once per
     * instance of [JdkProvider].
     * Other failures to provide a matching JDK are reported via the [JdkResult.Failure] type.
     */
    context(invalidJavaHomeReporter: ProblemReporter)
    suspend fun getJdk(
        criteria: JdkProvisioningCriteria,
        selectionMode: JdkSelectionMode,
    ): JdkResult = openTelemetry.tracer.spanBuilder("Get JDK ${criteria.majorVersion}")
        .setAttribute("selection-mode", selectionMode.name)
        .setAttribute("criteria.majorVersion", criteria.majorVersion)
        .setListAttribute("criteria.distributions", criteria.distributions?.map { it.schemaValue })
        .setListAttribute("criteria.os", criteria.operatingSystems.map { it.name })
        .setListAttribute("criteria.arch", criteria.architectures.map { it.name })
        .use { span ->
            when (selectionMode) {
                JdkSelectionMode.auto -> findJdkFromJavaHome(criteria) { unusableJavaHomeResult ->
                    logProvisioningFallback(unusableJavaHomeResult)
                    provisionJdk(criteria = criteria, unusableJavaHomeResult = unusableJavaHomeResult)
                }
                JdkSelectionMode.alwaysProvision -> provisionJdk(criteria, unusableJavaHomeResult = null)
                JdkSelectionMode.javaHome -> findJdkFromJavaHome(criteria) { it.toJdkResultFailure() }
            }.also {
                span.setAttribute("result", it.toString())
            }
        }

    private fun logProvisioningFallback(unusableJavaHomeResult: UnusableJavaHomeResult) {
        when (unusableJavaHomeResult) {
            is UnusableJavaHomeResult.UnsetOrEmpty -> {
                // Useful when the user makes a mistake when setting JAVA_HOME (doesn't source their profile or export
                // their variable), and want to understand what's going on.
                jdkProviderLogger.debug("JAVA_HOME is not set (or empty). Amper will provision a JDK instead.")
            }
            is UnusableJavaHomeResult.Mismatch -> {
                jdkProviderLogger.info("`JAVA_HOME` was found but doesn't match the JDK selection criteria: " +
                        "${unusableJavaHomeResult.reason}. Amper will provision a suitable JDK instead.")
            }
            is UnusableJavaHomeResult.Invalid -> Unit // already reported via messages (once and for all)
        }
    }

    private fun UnusableJavaHomeResult.toJdkResultFailure(): JdkResult.Failure {
        val message = when (this) {
            is UnusableJavaHomeResult.UnsetOrEmpty -> ProvisioningBundle.message("java.home.unusable.not.set")
            is UnusableJavaHomeResult.Invalid -> ProvisioningBundle.message("java.home.unusable.invalid")
            is UnusableJavaHomeResult.Mismatch -> ProvisioningBundle.message("java.home.unusable.mismatch", reason)
        }
        return JdkResult.Failure(message)
    }

    /**
     * Finds a JDK matching the given [criteria], downloads/extracts it to the cache, and returns the corresponding
     * [JdkResult].
     *
     * This function doesn't attempt to find a matching JDK in `JAVA_HOME`.
     * It directly searches via the Foojay Disco API.
     */
    suspend fun provisionJdk(criteria: JdkProvisioningCriteria): JdkResult =
        provisionJdk(criteria, unusableJavaHomeResult = null)

    private suspend fun provisionJdk(
        criteria: JdkProvisioningCriteria,
        unusableJavaHomeResult: UnusableJavaHomeResult?,
    ): JdkResult = openTelemetry.tracer.spanBuilder("Get provisioned JDK").use { span ->
        span.setAttribute("from-memory-cache", true)
        provisioningCache.computeIfAbsent(criteria) {
            span.setAttribute("from-memory-cache", false)
            getFromPersistentCacheOrProvision(criteria, unusableJavaHomeResult, provisioningSpan = span)
        }.also {
            span.setAttribute("result", it.toString())
        }
    }

    private suspend fun getFromPersistentCacheOrProvision(
        criteria: JdkProvisioningCriteria,
        unusableJavaHomeResult: UnusableJavaHomeResult?,
        provisioningSpan: Span, // we don't use Span.current() because it would be the incremental cache span
    ): JdkResult {
        provisioningSpan.setAttribute("from-persistent-cache", true)
        // We need to put the whole criteria as a key, because small differences could exist between modules.
        // If we wanted to use only the major version and distributions as key, 2 modules could have the same key
        // and yet have a different list of acknowledged licenses, which wiykd ruin caching on every build.
        val cacheKey = openTelemetry.tracer.spanBuilder("Computing cache key").use {
            "jdk-provisioning_${Json.encodeToString<JdkProvisioningCriteria>(criteria)}" // will be sanitized by IC
        }
        val cacheResult = incrementalCache.execute(
            key = cacheKey,
            inputValues = emptyMap(),
            inputFiles = emptyList(),
        ) {
            provisioningSpan.setAttribute("from-persistent-cache", false)
            val result = openTelemetry.tracer.spanBuilder("Provision JDK").use {
                jdkProvisioner.provision(criteria, unusableJavaHomeResult)
            }
            val outputFiles = when (result) {
                is JdkResult.Success -> listOf(result.jdk.homeDir)
                is JdkResult.Failure -> emptyList()
            }
            val serializedResult = openTelemetry.tracer.spanBuilder("Serialize JDK result for cache").use {
                Json.encodeToString(result)
            }
            IncrementalCache.ExecutionResult(
                outputFiles = outputFiles,
                outputValues = mapOf("jdkResult" to serializedResult),
                // We don't cache failures persistently, otherwise we can never recover from them.
                // We cache successful results indefinitely for maximum reproducibility. We could argue that we
                // should get patch versions ASAP, but it's better that the user controls when that happens (not in
                // the middle of a git bisect) - they can do `./amper clean` to clean the cache.
                expirationTime = if (result is JdkResult.Failure) Clock.System.now() else null,
            )
        }
        return openTelemetry.tracer.spanBuilder("Deserialize cached JDK result").use {
            Json.decodeFromString<JdkResult>(cacheResult.outputValues.getValue("jdkResult"))
        }
    }

    context(invalidJavaHomeReporter: ProblemReporter)
    private suspend fun findJdkFromJavaHome(
        jdkProvisioningCriteria: JdkProvisioningCriteria,
        fallback: suspend (UnusableJavaHomeResult) -> JdkResult,
    ): JdkResult = when (val info = javaHomeInfoProvider.getOrRead()) {
        is JavaHomeInfo.Valid -> when (val match = matchJavaHomeJdk(info, jdkProvisioningCriteria)) {
            is MatchResult.Match -> JdkResult.FoundInJavaHome(
                jdk = Jdk(
                    homeDir = info.path,
                    version = info.releaseInfo.javaVersion,
                    distribution = info.releaseInfo.detectedDistribution,
                    source = "JAVA_HOME",
                )
            )
            is MatchResult.Mismatch -> fallback(UnusableJavaHomeResult.Mismatch(match.reason))
            is MatchResult.Unknown -> fallback(UnusableJavaHomeResult.Mismatch("Cannot check whether JAVA_HOME matches the JDK selection criteria: ${match.error}"))
        }
        is JavaHomeInfo.Invalid -> fallback(UnusableJavaHomeResult.Invalid)
        is JavaHomeInfo.UnsetOrEmpty -> fallback(UnusableJavaHomeResult.UnsetOrEmpty)
    }

    private suspend fun matchJavaHomeJdk(
        info: JavaHomeInfo.Valid,
        jdkProvisioningCriteria: JdkProvisioningCriteria,
    ): MatchResult = openTelemetry.tracer.spanBuilder("Check JAVA_HOME suitability").use { span ->
        info.releaseInfo.match(jdkProvisioningCriteria).also { result ->
            span.setAttribute("result", result.toString())
        }
    }
}

private sealed interface MatchResult {
    /**
     * The JDK matches the criteria.
     */
    data object Match : MatchResult

    /**
     * The JDK doesn't match the criteria for the given [reason].
     */
    data class Mismatch(val reason: String) : MatchResult

    /**
     * An [error] prevents from getting information about the JDK, so we can't know if it matches the criteria.
     */
    data class Unknown(val error: String) : MatchResult
}

private fun ReleaseInfo.match(criteria: JdkProvisioningCriteria): MatchResult {
    if (criteria.majorVersion != majorJavaVersion) {
        return MatchResult.Mismatch(ProvisioningBundle.message("java.home.unusable.criterion.mismatch.major.version", criteria.majorVersion, majorJavaVersion))
    }

    val expectedDistributions = criteria.distributions ?: return MatchResult.Match // null means all distributions are accepted
    if (detectedDistribution == null) {
        return MatchResult.Unknown(ProvisioningBundle.message("java.home.unusable.unknown.distribution", implementor, releaseFile))
    }
    if (detectedDistribution !in expectedDistributions) {
        return MatchResult.Mismatch(ProvisioningBundle.message("java.home.unusable.criterion.mismatch.distribution", expectedDistributions.map { it.schemaValue }, detectedDistribution.schemaValue))
    }
    if (detectedDistribution.requiresLicense && detectedDistribution !in criteria.acknowledgedLicenses) {
        return MatchResult.Mismatch(ProvisioningBundle.message("java.home.unusable.criterion.mismatch.license.non.acknowledged", detectedDistribution.schemaValue))
    }
    return MatchResult.Match
}
