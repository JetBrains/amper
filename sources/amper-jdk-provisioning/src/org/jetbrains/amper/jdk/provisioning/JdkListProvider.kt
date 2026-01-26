/*
 * Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.jdk.provisioning

import io.opentelemetry.api.OpenTelemetry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import org.jetbrains.amper.frontend.schema.JvmDistribution
import org.jetbrains.amper.jdks.IjJdkArchitecture
import org.jetbrains.amper.jdks.IjJdkFamily
import org.jetbrains.amper.jdks.IjJdkOs
import org.jetbrains.amper.jdks.fetchIjJdks
import org.jetbrains.amper.system.info.Arch
import org.jetbrains.amper.system.info.OsFamily
import org.jetbrains.amper.telemetry.use

internal class JdkListProvider(
    private val openTelemetry: OpenTelemetry,
) {
    private val mutex = Mutex()
    private var jdkPackages: List<JdkPackage>? = null

    /**
     * Fetches the metadata of the JDKs that can be provisioned. The list is cached in memory.
     */
    suspend fun getOrFetch(): List<JdkPackage> = openTelemetry.tracer.spanBuilder("Get JDK list").use {
        jdkPackages ?: mutex.withLock {
            jdkPackages ?: withContext(Dispatchers.IO) {
                fetchAndConvertWithTelemetry().also { packages ->
                    jdkPackages = packages
                }
            }
        }
    }

    private suspend fun fetchAndConvertWithTelemetry(): List<JdkPackage> {
        val jdks = openTelemetry.tracer.spanBuilder("Fetch JDK list").use {
            fetchIjJdks()
        }
        return openTelemetry.tracer.spanBuilder("Convert JDK packages metadata").use {
            jdks.toJdkPackages()
        }
    }
}

@Serializable
internal data class JdkPackage(
    val majorVersion: Int,
    val fullVersion: String,
    val distribution: JvmDistribution,
    val architecture: Arch,
    val operatingSystem: OsFamily,
    val downloadUrl: String,
) {
    override fun toString(): String = "${distribution.name} $fullVersion"
}

private fun List<IjJdkFamily>.toJdkPackages() = filter { !it.preview }.flatMap { it.toJdkPackages() }

private fun IjJdkFamily.toJdkPackages(): List<JdkPackage> = packages.mapNotNull { pkg ->
    JdkPackage(
        majorVersion = jdkVersionMajor,
        fullVersion = pkg.version,
        distribution = jvmDistributionOf(vendor, product) ?: return@mapNotNull null,
        architecture = pkg.arch.toArchitecture(),
        operatingSystem = pkg.os.toOperatingSystem(),
        downloadUrl = pkg.url,
    )
}

private fun jvmDistributionOf(vendor: String, productName: String): JvmDistribution? = when (vendor.lowercase()) {
    "alibaba" -> JvmDistribution.AlibabaDragonwell
    "amazon" -> JvmDistribution.AmazonCorretto
    "azul" -> JvmDistribution.AzulZulu
    "bellsoft" -> JvmDistribution.BellSoftLiberica
    "eclipse" -> JvmDistribution.EclipseTemurin
    "graalvm" -> JvmDistribution.GraalVMCommunityEdition
    "ibm" -> JvmDistribution.IbmSemeru
    "jetbrains" -> JvmDistribution.JetBrainsRuntime
    "microsoft" -> JvmDistribution.Microsoft
    "oracle" -> when (productName.lowercase()) {
        "graalvm" -> JvmDistribution.OracleGraalVM
        "openjdk" -> JvmDistribution.OracleOpenJdk
        else -> null
    }
    "sap" -> JvmDistribution.SapMachine
    else -> null
}

private fun IjJdkArchitecture.toArchitecture(): Arch = when (this) {
    IjJdkArchitecture.x64 -> Arch.X64
    IjJdkArchitecture.aarch64 -> Arch.Arm64
}

private fun IjJdkOs.toOperatingSystem(): OsFamily = when (this) {
    IjJdkOs.Linux -> OsFamily.Linux
    IjJdkOs.MacOS -> OsFamily.MacOs
    IjJdkOs.Windows -> OsFamily.Windows
}
