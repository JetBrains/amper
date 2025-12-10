/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.jdk.provisioning

import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.mapNotNull
import org.jetbrains.amper.core.AmperUserCacheRoot
import org.jetbrains.amper.core.downloader.Downloader
import org.jetbrains.amper.core.extract.extractFileToCacheLocation
import org.jetbrains.amper.foojay.api.DiscoApiClient
import org.jetbrains.amper.foojay.api.DiscoPackage
import org.jetbrains.amper.foojay.api.RequestedVersion
import org.jetbrains.amper.foojay.model.ArchiveType
import org.jetbrains.amper.foojay.model.Distribution
import org.jetbrains.amper.foojay.model.PackageType
import org.jetbrains.amper.frontend.schema.JvmDistribution
import org.jetbrains.annotations.Nls
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name

/**
 * Finds a JDK matching the given [criteria], downloads/extracts it to the cache, and returns the corresponding
 * [JdkResult].
 */
internal suspend fun DiscoApiClient.provisionJdk(
    userCacheRoot: AmperUserCacheRoot,
    criteria: JdkProvisioningCriteria,
    unusableJavaHomeResult: UnusableJavaHomeResult?,
): JdkResult {
    val distributions = (criteria.distributions ?: JvmDistribution.entries).map { it.toDiscoApiDistro() }
    val packages = listPackages(
        version = RequestedVersion.LatestOfMajor(majorJdkVersion = criteria.majorVersion),
        packageType = PackageType.JDK,
        distributions = distributions,
        operatingSystems = criteria.operatingSystems,
        architectures = criteria.architectures,
        archiveTypes = listOf(ArchiveType.TAR_GZ, ArchiveType.TGZ, ArchiveType.ZIP), // only what we can extract
        // JavaFX is no longer supported as of March 2025, and even Oracle's JDK 8 no longer bundles JavaFX by default.
        // Users should have been using OpenJFX instead since September 2018. See https://www.oracle.com/javase/javafx/.
        // If we get strong demand some day (doubtful), we'll make it a frontend opt-in and set it here accordingly.
        javafxBundled = false,
        directlyDownloadable = true, // we have to filter this, otherwise we just can't download them
        freeToUseInProduction = null, // we don't filter those, but we prioritize the free ones first when sorting
    )
        // Some distributions have a debuginfo archive result which only contains debug symbols and is not a valid JDK
        // in itself (e.g., BiSheng 11). We filter these out because we only want valid JDK results.
        .filterNot { it.isDebugInfoArchive() }

    if (packages.isEmpty()) {
        return JdkResult.Failure(noMatchingJdkErrorMessage(criteria))
    }

    val jdk = packages.sortedWith(comparingByDistroOrderAndFreeToUseFirst(distributions))
        .asFlow()
        .mapNotNull { pkg ->
            // Null should never be returned here, because the API should only return valid IDs with valid download URLs
            // when we set directly_downloadable=true. That said, we can't really trust it blindly because it could
            // block users completely. Because of this, we only skip the package here (the error is already logged), as
            // if it weren't part of the results in the first place.
            pkg.downloadOrNull(userCacheRoot)
        }
        .firstOrNull()

    if (jdk == null) {
        jdkProviderLogger.error("${packages.size} JDKs found matching the criteria, but none of them could be downloaded")
        return JdkResult.Failure(noMatchingJdkErrorMessage(criteria))
    }
    return JdkResult.Provisioned(jdk = jdk, unusableJavaHomeResult = unusableJavaHomeResult)
}

/**
 * Returns true if the package is a debuginfo package (not a real JDK).
 * It's usually the first one that's returned for BiSheng 11 (and we don't want it!).
 */
private fun DiscoPackage.isDebugInfoArchive(): Boolean = fileName?.contains("debuginfo") == true

private fun noMatchingJdkErrorMessage(criteria: JdkProvisioningCriteria): @Nls String = buildString {
    appendLine(ProvisioningBundle.message("jdk.provisioning.no.matching.jdks.intro"))

    with(criteria) {
        appendCriterionLine("jdk.provisioning.criterion.major.version", majorVersion)
        appendCriterionLine("jdk.provisioning.criterion.os", operatingSystems.joinToString { it.apiValue })
        appendCriterionLine("jdk.provisioning.criterion.arch", architectures.joinToString { it.apiValue })
        appendCriterionLine("jdk.provisioning.criterion.libc.type", operatingSystems.joinToString { it.libCType.apiValue })
        appendCriterionLine("jdk.provisioning.criterion.acceptable.distributions", acceptableDistrosUserVisibleValue())
    }
}.trim()

private fun JdkProvisioningCriteria.acceptableDistrosUserVisibleValue(): String {
    val explicitList = distributions?.joinToString { it.schemaValue }
    if (explicitList == null) {
        return if (acknowledgedLicenses.isEmpty()) {
            ProvisioningBundle.message("jdk.provisioning.criterion.acceptable.distributions.any")
        } else {
            ProvisioningBundle.message(
                "jdk.provisioning.criterion.acceptable.distributions.anyFreeOrOneOf",
                acknowledgedLicenses.joinToString { it.schemaValue }
            )
        }
    }
    return explicitList
}

private fun StringBuilder.appendCriterionLine(messageKey: String, value: Any?) {
    appendLine("  - " + ProvisioningBundle.message(messageKey, value))
}

private fun comparingByDistroOrderAndFreeToUseFirst(orderedDistributions: List<Distribution>): Comparator<DiscoPackage> {
    val priority = orderedDistributions.mapIndexed { index, distribution -> distribution to index }.toMap()
    return compareBy<DiscoPackage> { priority[it.distribution] ?: Int.MAX_VALUE }
        .thenBy { if (it.freeToUseInProduction == true) 0 else 1 }
}

/**
 * Finds the download URL of the package, downloads it to the cache, and returns the corresponding [Jdk].
 *
 * Returns `null` if the package is not directly downloadable, or if the download URL cannot be retrieved.
 * This isn't expected to happen, but it's unclear whether we can trust the DiscoAPI results when setting
 * `directly_downloadable` to true.
 * In this case, an error will be logged and this package should just be skipped.
 */
context(discoApiClient: DiscoApiClient)
private suspend fun DiscoPackage.downloadOrNull(userCacheRoot: AmperUserCacheRoot): Jdk? {
    val packageDetails = discoApiClient.getPackageDetails(id)
    if (packageDetails == null) {
        // This should never happen (the API should only return valid IDs), but we can't really trust it blindly because
        // it could block users completely. Because of this, we only log an error here and skip the package by
        // returning null here, as if it weren't part of the results in the first place.
        jdkProviderLogger.error("Couldn't find package details for id={} ({} {})", id, distribution, majorVersion)
        return null
    }
    val directDownloadUri = packageDetails.directDownloadUri
    if (directDownloadUri == null) {
        jdkProviderLogger.error("Download URI unavailable for id={} ({} {})", id, distribution, majorVersion)
        return null
    }
    val jdkArchive = Downloader.downloadFileToCacheLocation(directDownloadUri, userCacheRoot)
    val extractedJdkRoot = extractFileToCacheLocation(jdkArchive, userCacheRoot)
    return Jdk(
        homeDir = extractedJdkRoot.findHomeDir(),
        version = javaVersion,
        distribution = distribution.toJvmDistribution(),
        source = directDownloadUri,
    )
}

/**
 * Finds a valid JDK home directory in this directory.
 */
private tailrec fun Path.findHomeDir(): Path {
    if (resolve("bin/javac").exists() || resolve("bin/javac.exe").exists()) {
        return this
    }
    // A lot of archives for macOS contain the JDK under <root>/Contents/Home (e.g. Corretto, Temurin)
    // or <root>/<someDir>/Contents/Home (e.g. Zulu 8/11/17/21, Microsoft 11, ...)
    val contentsHome = resolve("Contents/Home")
    if (contentsHome.isDirectory()) {
        return contentsHome
    }
    // A lot of archives for macOS contain one more single root directory that we need to go through (e.g. Zulu 8,
    // Microsoft 11, OpenLogic 11), sometimes next to some other files like READMEs (e.g. Zulu 11/17/21).
    val directories = listDirectoryEntries().filter { it.isDirectory() }
    if (directories.size == 1) {
        return directories.single().findHomeDir()
    }
    // OpenLogic 8 for mac has a strange layout that looks like a JDK with a `bin`, `jre`, `lib` directory, but actually
    // contains almost nothing in `bin`. Instead, a 'jdk1.8.0_462.jdk' directory is present and contains the real JDK.
    // Maybe other archives are like this too. In any case, we need to handle this.
    val jdkDirs = directories.filter { it.name.startsWith("jdk", ignoreCase = true) }
    if (jdkDirs.size == 1) {
        return jdkDirs.single().findHomeDir()
    }
    error("Couldn't find JDK home in $this")
}

private fun JvmDistribution.toDiscoApiDistro(): Distribution = when (this) {
    JvmDistribution.EclipseTemurin -> Distribution.TEMURIN
    JvmDistribution.AzulZulu -> Distribution.ZULU
    JvmDistribution.AmazonCorretto -> Distribution.CORRETTO
    JvmDistribution.JetBrainsRuntime -> Distribution.JETBRAINS
    JvmDistribution.Oracle -> Distribution.ORACLE
    JvmDistribution.OracleOpenJdk -> Distribution.ORACLE_OPEN_JDK
    JvmDistribution.Microsoft -> Distribution.MICROSOFT
    JvmDistribution.Bisheng -> Distribution.BISHENG
    JvmDistribution.AlibabaDragonwell -> Distribution.DRAGONWELL
    JvmDistribution.TencentKona -> Distribution.KONA
    JvmDistribution.BellSoftLiberica -> Distribution.LIBERICA
    JvmDistribution.PerforceOpenLogic -> Distribution.OPEN_LOGIC
    JvmDistribution.SapMachine -> Distribution.SAP_MACHINE
    JvmDistribution.IbmSemeru -> Distribution.SEMERU
    JvmDistribution.IbmSemeruCertified -> Distribution.SEMERU_CERTIFIED
}

private fun Distribution.toJvmDistribution(): JvmDistribution = when (this) {
    Distribution.TEMURIN -> JvmDistribution.EclipseTemurin
    Distribution.ZULU -> JvmDistribution.AzulZulu
    Distribution.CORRETTO -> JvmDistribution.AmazonCorretto
    Distribution.JETBRAINS -> JvmDistribution.JetBrainsRuntime
    Distribution.ORACLE -> JvmDistribution.Oracle
    Distribution.ORACLE_OPEN_JDK -> JvmDistribution.OracleOpenJdk
    Distribution.MICROSOFT -> JvmDistribution.Microsoft
    Distribution.BISHENG -> JvmDistribution.Bisheng
    Distribution.DRAGONWELL -> JvmDistribution.AlibabaDragonwell
    Distribution.KONA -> JvmDistribution.TencentKona
    Distribution.LIBERICA -> JvmDistribution.BellSoftLiberica
    Distribution.OPEN_LOGIC -> JvmDistribution.PerforceOpenLogic
    Distribution.SAP_MACHINE -> JvmDistribution.SapMachine
    Distribution.SEMERU -> JvmDistribution.IbmSemeru
    Distribution.SEMERU_CERTIFIED -> JvmDistribution.IbmSemeruCertified
}
