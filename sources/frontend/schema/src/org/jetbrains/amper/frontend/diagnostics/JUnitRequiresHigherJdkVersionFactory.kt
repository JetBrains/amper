/*
 * Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.diagnostics

import org.apache.maven.artifact.versioning.ComparableVersion
import org.jetbrains.amper.frontend.AmperModule
import org.jetbrains.amper.frontend.SchemaBundle
import org.jetbrains.amper.frontend.api.Trace
import org.jetbrains.amper.frontend.api.TraceableString
import org.jetbrains.amper.frontend.api.TraceableValue
import org.jetbrains.amper.frontend.asBuildProblemSource
import org.jetbrains.amper.frontend.types.generated.*
import org.jetbrains.amper.problems.reporting.BuildProblem
import org.jetbrains.amper.problems.reporting.BuildProblemId
import org.jetbrains.amper.problems.reporting.BuildProblemSource
import org.jetbrains.amper.problems.reporting.BuildProblemType
import org.jetbrains.amper.problems.reporting.DiagnosticId
import org.jetbrains.amper.problems.reporting.FileBuildProblemSource
import org.jetbrains.amper.problems.reporting.Level
import org.jetbrains.amper.problems.reporting.MultipleLocationsBuildProblemSource
import org.jetbrains.amper.problems.reporting.ProblemReporter

private val JUnit6 = ComparableVersion("6.0.0")

object JUnitRequiresHigherJdkVersionFactory : AomSingleModuleDiagnosticFactory {
    @Deprecated(
        message = "Use JUnitRequiresHigherJdkVersion.ID",
        replaceWith = ReplaceWith("JUnitRequiresHigherJdkVersion.ID"),
    )
    val diagnosticId: BuildProblemId = JUnitRequiresHigherJdkVersion.ID

    override fun analyze(module: AmperModule, problemReporter: ProblemReporter) {
        val reportedPlaces = mutableSetOf<Pair<Trace, Trace>>()
        module.fragments.forEach { fragment ->
            val junitPlatformVersion = fragment.settings.jvm.test.junitPlatformVersion
            val minJdkVersionForJunit = minJdkVersionForJunit(junitPlatformVersion)
            if (fragment.settings.jvm.jdk.version < minJdkVersionForJunit) {
                val alreadyReported = !reportedPlaces.add(Pair(
                    fragment.settings.jvm.test.junitPlatformVersionDelegate.trace,
                    fragment.settings.jvm.jdk.versionDelegate.trace,
                ))
                if (!alreadyReported) {
                    problemReporter.reportMessage(
                        JUnitRequiresHigherJdkVersion(
                            junitPlatformVersion = TraceableString(
                                value = junitPlatformVersion,
                                trace = fragment.settings.jvm.test.junitPlatformVersionDelegate.trace,
                            ),
                            actualJdkVersion = TraceableValue(
                                value = fragment.settings.jvm.jdk.version,
                                trace = fragment.settings.jvm.jdk.versionDelegate.trace,
                            ),
                            minJdkVersion = minJdkVersionForJunit,
                        )
                    )
                }
            }
        }
    }

    private fun minJdkVersionForJunit(junitPlatformVersion: String): Int =
        if (ComparableVersion(junitPlatformVersion) >= JUnit6) 17 else 8
}

class JUnitRequiresHigherJdkVersion(
    val junitPlatformVersion: TraceableString,
    val actualJdkVersion: TraceableValue<Int>,
    val minJdkVersion: Int,
) : BuildProblem {
    companion object {
        const val ID = "junit.platform.requires.higher.jdk"
    }

    @Deprecated("Should be replaced with `diagnosticId` property", replaceWith = ReplaceWith("diagnosticId"))
    override val buildProblemId = ID
    override val diagnosticId: DiagnosticId = FrontendDiagnosticId.JUnitRequiresHigherJdkVersion
    override val message = SchemaBundle.message(
        messageKey = "junit.platform.requires.higher.jdk",
        junitPlatformVersion.value, minJdkVersion, actualJdkVersion.value,
    )
    override val level: Level = Level.Error
    override val type: BuildProblemType = BuildProblemType.InconsistentConfiguration
    override val source: BuildProblemSource = MultipleLocationsBuildProblemSource(
        sources = listOf(
            junitPlatformVersion.asBuildProblemSource(),
            actualJdkVersion.asBuildProblemSource(),
        ).filterIsInstance<FileBuildProblemSource>(),
        groupingMessage = message,
    )
}
