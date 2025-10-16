/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.jdk.provisioning

import io.ktor.client.plugins.*
import io.ktor.client.plugins.cache.HttpCache
import io.ktor.client.plugins.cache.storage.FileStorage
import io.ktor.client.plugins.logging.*
import org.jetbrains.amper.core.AmperUserCacheRoot
import org.jetbrains.amper.core.UsedInIdePlugin
import org.jetbrains.amper.core.UsedVersions
import org.jetbrains.amper.core.downloader.AmperUserAgent
import org.jetbrains.amper.foojay.api.DiscoApiClient
import org.jetbrains.amper.foojay.model.Architecture
import org.jetbrains.amper.foojay.model.OperatingSystem
import org.jetbrains.amper.frontend.schema.JdkSelectionMode
import org.jetbrains.amper.frontend.schema.JdkSettings
import org.jetbrains.amper.frontend.schema.JvmDistribution
import org.jetbrains.amper.problems.reporting.ProblemReporter
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import kotlin.io.path.div
import io.ktor.client.plugins.logging.Logger as KtorLogger

data class JdkProvisioningCriteria(
    val majorVersion: Int = UsedVersions.defaultJdkVersion,
    val distributions: List<JvmDistribution>? = null,
    val acknowledgedLicenses: List<JvmDistribution> = emptyList(),
    val operatingSystems: List<OperatingSystem> = listOf(OperatingSystem.current()),
    val architectures: List<Architecture> = listOf(Architecture.current()),
)

internal val jdkProviderLogger: Logger = LoggerFactory.getLogger(JdkProvider::class.java)

class JdkProvider(
    private val userCacheRoot: AmperUserCacheRoot,
) : AutoCloseable {
    private val discoApiClient = DiscoApiClient {
        install(UserAgent) {
            agent = AmperUserAgent
        }

        // Debug logs for calls to the DiscoAPI
        install(Logging) {
            level = LogLevel.INFO // INFO = just HTTP method and URL, no headers or body
            logger = object : KtorLogger {
                override fun log(message: String): Unit = jdkProviderLogger.debug(message)
            }
        }

        install(HttpCache) {
            val cacheFile = userCacheRoot.path / "jdk-discovery-cache"
            publicStorage(FileStorage(cacheFile.toFile()))
        }
    }

    private val javaHomeInfoProvider = JavaHomeInfoProvider()

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
        criteria: JdkProvisioningCriteria = JdkProvisioningCriteria(),
        selectionMode: JdkSelectionMode,
    ): JdkResult = when (selectionMode) {
        JdkSelectionMode.auto -> findJdkFromJavaHome(criteria) { unusableJavaHomeResult ->
            when (unusableJavaHomeResult) {
                is UnusableJavaHomeResult.UnsetOrEmpty -> Unit // nothing to say here
                is UnusableJavaHomeResult.Invalid -> Unit // already reported via messages (once and for all)
                is UnusableJavaHomeResult.Mismatch -> jdkProviderLogger
                    .info("`JAVA_HOME` was found but doesn't match the JDK selection criteria: " +
                            "${unusableJavaHomeResult.reason}. Amper will provision a suitable JDK instead.")
            }
            discoApiClient.provisionJdk(
                userCacheRoot = userCacheRoot,
                criteria = criteria,
                unusableJavaHomeResult = unusableJavaHomeResult,
            )
        }
        JdkSelectionMode.alwaysProvision -> provisionJdk(criteria)
        JdkSelectionMode.javaHome -> findJdkFromJavaHome(criteria) {
            val message = when (it) {
                is UnusableJavaHomeResult.UnsetOrEmpty -> ProvisioningBundle.message("java.home.unusable.not.set")
                is UnusableJavaHomeResult.Invalid -> ProvisioningBundle.message("java.home.unusable.invalid")
                is UnusableJavaHomeResult.Mismatch -> ProvisioningBundle.message("java.home.unusable.mismatch", it.reason)
            }
            JdkResult.Failure(message)
        }
    }

    /**
     * Finds a JDK matching the given [criteria], downloads/extracts it to the cache, and returns the corresponding
     * [JdkResult].
     *
     * This function doesn't attempt to find a matching JDK in `JAVA_HOME`.
     * It directly searches via the Foojay Disco API.
     */
    suspend fun provisionJdk(criteria: JdkProvisioningCriteria = JdkProvisioningCriteria()): JdkResult =
        discoApiClient.provisionJdk(
            userCacheRoot = userCacheRoot,
            criteria = criteria,
            unusableJavaHomeResult = null,
        )

    context(invalidJavaHomeReporter: ProblemReporter)
    private suspend fun findJdkFromJavaHome(
        jdkProvisioningCriteria: JdkProvisioningCriteria,
        fallback: suspend (UnusableJavaHomeResult) -> JdkResult,
    ): JdkResult =
        when (val info = javaHomeInfoProvider.getOrRead()) {
            is JavaHomeInfo.Valid -> when (val match = info.releaseInfo.match(jdkProvisioningCriteria)) {
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

    override fun close() {
        discoApiClient.close()
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
        return MatchResult.Mismatch(ProvisioningBundle.message("java.home.unusable.criterion.mismatch.distribution", expectedDistributions.joinToString { it.schemaValue }, detectedDistribution.schemaValue))
    }
    if (detectedDistribution.requiresLicense && detectedDistribution !in criteria.acknowledgedLicenses) {
        return MatchResult.Mismatch(ProvisioningBundle.message("java.home.unusable.criterion.mismatch.license.non.acknowledged", detectedDistribution.schemaValue))
    }
    return MatchResult.Match
}
