/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.jdk.provisioning

import org.jetbrains.amper.frontend.schema.JvmDistribution
import java.nio.file.Path
import kotlin.io.path.useLines

/**
 * Reads the JDK release information from this `release` file.
 */
internal fun Path.readReleaseInfo(): ReleaseInfo {
    val properties = useLines { lines ->
        lines.associate { line ->
            val (key, value) = line.split('=')
            key to value.trim().removeSurrounding("\"")
        }
    }
    return ReleaseInfo(
        releaseFile = this,
        properties = properties,
    )
}

/**
 * Thrown when the `release` file in the JDK is invalid (does not contain the mandatory properties).
 */
internal class InvalidReleaseInfoException(message: String) : Exception(message)

/**
 * Represents the information present in the `release` file at the root of a Java distribution.
 */
internal data class ReleaseInfo(
    /**
     * The path to the `release` file.
     */
    val releaseFile: Path,
    /**
     * The raw properties present in the `release` file.
     */
    private val properties: Map<String, String>,
) {
    /**
     * The full Java version string (from `JAVA_VERSION`). Usually present in all JDK distributions.
     *
     * It can have an arbitrary number of components. Sometimes it can be just a major version number.
     *
     * Examples: `21.0.1`, `1.8.0_462`, `25`
     */
    val javaVersion: String = properties["JAVA_VERSION"]
        ?: throw InvalidReleaseInfoException("JAVA_VERSION property is missing")

    /**
     * The major Java version number, as an integer.
     *
     * Old releases following the 1.6, 1.7, 1.8 pattern result in the corresponding integer 6, 7, 8.
     */
    val majorJavaVersion: Int = javaVersion.removePrefix("1.").substringBefore('.').toIntOrNull()
        ?: throw InvalidReleaseInfoException("Cannot read major version from JAVA_VERSION value '$javaVersion'")

    /**
     * The implementor (vendor) of the JDK or JRE (from `IMPLEMENTOR`). Usually present in all JDK distributions.
     *
     * Examples: `JetBrains s.r.o.`, `Azul Systems, Inc.`, `IBM Corporation`, `Eclipse Adoptium`, `Amazon.com Inc.`
     */
    val implementor: String? = properties["IMPLEMENTOR"]

    /**
     * An optional vendor-specific version string. It can have prefixes and arbitrary numbers.
     *
     * Examples: `Corretto-21.0.1.12.1` (Amazon), `Zing25.09.0.0+3` (Azul Zulu Prime), `25.0.0.0` (IBM)
     */
    internal val implementorVersion: String? = properties["IMPLEMENTOR_VERSION"]

    /**
     * An optional URL to the repository hosting the JDK sources.
     */
    internal val sourceRepo: String? = properties["SOURCE_REPO"]

    /**
     * An optional opaque string that usually contains the commit hash of the JDK sources (among other things).
     */
    internal val source: String? = properties["SOURCE"]

    /**
     * The detected [JvmDistribution] from this JDK `release` file.
     */
    val detectedDistribution: JvmDistribution? = detectDistribution()

    private fun detectDistribution(): JvmDistribution? {
        if (implementor == null) {
            return null
        }
        val lci = implementor.lowercase()
        return when {
            lci.startsWith("alibaba") -> JvmDistribution.AlibabaDragonwell
            lci.startsWith("amazon") -> JvmDistribution.AmazonCorretto
            lci.startsWith("azul") -> if (implementorVersion?.startsWith("Zing", ignoreCase = true) == true) {
                JvmDistribution.AzulZuluPrime
            } else {
                JvmDistribution.AzulZulu
            }
            lci.startsWith("bellsoft") -> JvmDistribution.BellSoftLiberica
            lci.startsWith("bisheng") -> JvmDistribution.Bisheng
            lci.startsWith("eclipse") -> JvmDistribution.EclipseTemurin
            lci.startsWith("ibm") || lci.startsWith("international business") ->
                if (sourceRepo != null && "git@github.ibm.com" in sourceRepo) {
                    // the only difference between the certified and open version of Semeru is the git host, unfortunately
                    JvmDistribution.IbmSemeruCertified
                } else {
                    JvmDistribution.IbmSemeru
                }
            lci.startsWith("jetbrains") -> JvmDistribution.JetBrainsRuntime
            lci.startsWith("microsoft") -> JvmDistribution.Microsoft
            lci.startsWith("openlogic") -> JvmDistribution.PerforceOpenLogic
            lci.startsWith("oracle") -> if (source != null && "open:git:" in source) {
                // This might seem counter-intuitive, but the non-open Oracle JDK contains both git: and open:git: refs
                // like this: ".:git:49d6a23b8806 open:git:6c48f4ed707b", while the Oracle OpenJDK variant contains only
                // a regular ".:git:6c48f4ed707b".
                JvmDistribution.Oracle
            } else {
                JvmDistribution.OracleOpenJdk
            }
            lci.startsWith("sap") -> JvmDistribution.SapMachine
            lci.startsWith("tencent") -> JvmDistribution.TencentKona
            else -> null
        }
    }
}
