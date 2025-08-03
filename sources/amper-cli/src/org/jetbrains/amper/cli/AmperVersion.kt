/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.cli

import kotlinx.datetime.LocalDate
import kotlinx.datetime.format
import org.jetbrains.amper.buildinfo.AmperBuild
import org.jetbrains.amper.core.telemetry.spanBuilder
import org.jetbrains.amper.incrementalcache.computeClassPathHash
import org.jetbrains.amper.telemetry.useWithoutCoroutines
import org.jetbrains.amper.util.toLocalDateTimeInDefaultTimezone

object AmperVersion {

    private val distributionHash by lazy {
        spanBuilder("Compute classpath hash").useWithoutCoroutines {
            computeClassPathHash()
        }
    }

    /**
     * The Amper product name and version.
     */
    val banner: String by lazy {
        with(AmperBuild) {
            val commitDate = commitInstant?.toLocalDateTimeInDefaultTimezone()?.date?.format(LocalDate.Formats.ISO)
            val distInfoIfSnapshot = if (isSNAPSHOT) "\nDistribution hash: $distributionHash" else ""
            "JetBrains Amper version $mavenVersion ($commitShortHash, $commitDate)$distInfoIfSnapshot"
        }
    }

    /**
     * A reliable number representing the identity of the Amper code being run.
     *
     * In non-snapshot builds (release or dev), this is the maven version (which uniquely identifies the build).
     * In snapshot builds (for example, from sources), this includes the maven version and the distribution jars hash.
     */
    val codeIdentifier: String by lazy {
        with(AmperBuild) {
            if (isSNAPSHOT) "$mavenVersion+$distributionHash" else mavenVersion
        }
    }
}
