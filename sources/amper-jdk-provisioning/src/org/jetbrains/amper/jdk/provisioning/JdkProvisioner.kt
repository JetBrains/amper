/*
 * Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.jdk.provisioning

import io.opentelemetry.api.OpenTelemetry
import org.jetbrains.amper.core.AmperUserCacheRoot
import org.jetbrains.amper.core.downloader.Downloader
import org.jetbrains.amper.core.extract.extractFileToCacheLocation
import org.jetbrains.amper.frontend.schema.JvmDistribution
import org.jetbrains.amper.telemetry.use
import org.jetbrains.annotations.Nls

internal class JdkProvisioner(
    private val openTelemetry: OpenTelemetry,
    private val userCacheRoot: AmperUserCacheRoot,
) {
    private val jdkListProvider = JdkListProvider(openTelemetry)

    /**
     * Finds a JDK matching the given [criteria], downloads/extracts it to the cache, and returns the corresponding
     * [JdkResult].
     */
    suspend fun provision(
        criteria: JdkProvisioningCriteria,
        unusableJavaHomeResult: UnusableJavaHomeResult?,
    ): JdkResult {
        val jdkList = jdkListProvider.getOrFetch()
        val matchingPackage = openTelemetry.tracer.spanBuilder("Select JDK").use {
            jdkList.selectMatchingPackage(criteria)
        }
        if (matchingPackage == null) {
            return JdkResult.Failure(noMatchingJdkErrorMessage(criteria))
        }
        val jdk = openTelemetry.tracer.spanBuilder("Download JDK $matchingPackage").use {
            matchingPackage.downloadToCache(userCacheRoot)
        }
        return JdkResult.Provisioned(jdk = jdk, unusableJavaHomeResult = unusableJavaHomeResult)
    }
}

private fun List<JdkPackage>.selectMatchingPackage(criteria: JdkProvisioningCriteria): JdkPackage? = asSequence()
    .filter {
        it.majorVersion == criteria.majorVersion
                && (criteria.distributions == null || it.distribution in criteria.distributions)
                && it.architecture in criteria.architectures
                && it.operatingSystem in criteria.operatingSystems
    }
    .sortedWith(comparingByDistroOrder(criteria.distributions ?: JvmDistribution.entries))
    .firstOrNull()

private fun comparingByDistroOrder(orderedDistributions: List<JvmDistribution>): Comparator<JdkPackage> {
    val priority = orderedDistributions.mapIndexed { index, distribution -> distribution to index }.toMap()
    return compareBy { priority[it.distribution] ?: Int.MAX_VALUE }
}

private fun noMatchingJdkErrorMessage(criteria: JdkProvisioningCriteria): @Nls String = buildString {
    appendLine(ProvisioningBundle.message("jdk.provisioning.no.matching.jdks.intro"))

    with(criteria) {
        appendCriterionLine("jdk.provisioning.criterion.major.version", majorVersion)
        appendCriterionLine("jdk.provisioning.criterion.os", operatingSystems.joinToString { it.displayName })
        appendCriterionLine("jdk.provisioning.criterion.arch", architectures.joinToString { it.displayName })
        appendCriterionLine("jdk.provisioning.criterion.acceptable.distributions", acceptableDistrosUserVisibleValue())
    }
}.trim()

private fun StringBuilder.appendCriterionLine(messageKey: String, value: Any?) {
    appendLine("  - " + ProvisioningBundle.message(messageKey, value))
}

private fun JdkProvisioningCriteria.acceptableDistrosUserVisibleValue(): String {
    val explicitList = distributions?.joinToString { it.schemaValue }
    if (explicitList == null) {
        return if (acknowledgedLicenses.isEmpty()) {
            ProvisioningBundle.message("jdk.provisioning.criterion.acceptable.distributions.anyFree")
        } else {
            ProvisioningBundle.message(
                "jdk.provisioning.criterion.acceptable.distributions.anyFreeOrOneOf",
                acknowledgedLicenses.joinToString { it.schemaValue }
            )
        }
    }
    return explicitList
}

/**
 * Downloads and extracts this JDK to the cache, and returns the corresponding [Jdk].
 *
 * Returns `null` if the package is not directly downloadable, or if the download URL cannot be retrieved.
 * This isn't expected to happen, but it's unclear whether we can trust the DiscoAPI results when setting
 * `directly_downloadable` to true.
 * In this case, an error will be logged and this package should just be skipped.
 */
private suspend fun JdkPackage.downloadToCache(userCacheRoot: AmperUserCacheRoot): Jdk {
    val jdkArchive = Downloader.downloadFileToCacheLocation(downloadUrl, userCacheRoot)
    val extractedJdkRoot = extractFileToCacheLocation(jdkArchive, userCacheRoot)
    return Jdk(
        homeDir = extractedJdkRoot.findValidJdkHomeDir(),
        version = fullVersion,
        distribution = distribution,
        source = downloadUrl,
    )
}
