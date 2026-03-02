/*
 * Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.diagnostics

import org.jetbrains.amper.frontend.AmperModule
import org.jetbrains.amper.frontend.SchemaBundle
import org.jetbrains.amper.frontend.api.Trace
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

private const val MinJavaVersionForJIC = 16

object JavaIncrementalCompilationRequiresJava16Factory : AomSingleModuleDiagnosticFactory {
    @Deprecated(
        message = "Use JavaIncrementalCompilationRequiresJava16.ID",
        replaceWith = ReplaceWith("JavaIncrementalCompilationRequiresJava16.ID"),
    )
    val diagnosticId: BuildProblemId = JavaIncrementalCompilationRequiresJava16.ID

    override fun analyze(module: AmperModule, problemReporter: ProblemReporter) {
        val reportedPlaces = mutableSetOf<Pair<Trace, Trace>>()
        module.fragments.forEach { fragment ->
            val compileIncrementally = fragment.settings.java.compileIncrementally
            if (compileIncrementally && fragment.settings.jvm.jdk.version < MinJavaVersionForJIC) {
                val alreadyReported = !reportedPlaces.add(Pair(
                    fragment.settings.java.compileIncrementallyDelegate.trace,
                    fragment.settings.jvm.jdk.versionDelegate.trace,
                ))
                if (!alreadyReported) {
                    problemReporter.reportMessage(
                        JavaIncrementalCompilationRequiresJava16(
                            incrementalCompilationTrace = fragment.settings.java.compileIncrementallyDelegate.trace,
                            actualJdkVersion = fragment.settings.jvm.jdk.version,
                            actualJdkVersionTrace = fragment.settings.jvm.jdk.versionDelegate.trace,
                            minJdkVersion = MinJavaVersionForJIC,
                        )
                    )
                }
            }
        }
    }
}

class JavaIncrementalCompilationRequiresJava16(
    val incrementalCompilationTrace: Trace,
    val actualJdkVersion: Int,
    val actualJdkVersionTrace: Trace,
    val minJdkVersion: Int,
) : BuildProblem {
    companion object {
        const val ID = "java.incremental.compilation.requires.higher.jdk"
    }

    @Deprecated("Should be replaced with `diagnosticId` property", replaceWith = ReplaceWith("diagnosticId"))
    override val buildProblemId = ID
    override val diagnosticId: DiagnosticId = FrontendDiagnosticId.JavaIncrementalCompilationRequiresJava16
    override val message = SchemaBundle.message("java.incremental.compilation.requires.higher.jdk", minJdkVersion, actualJdkVersion)
    override val level: Level = Level.Error
    override val type: BuildProblemType = BuildProblemType.InconsistentConfiguration
    override val source: BuildProblemSource = MultipleLocationsBuildProblemSource(
        sources = listOf(
            incrementalCompilationTrace.asBuildProblemSource(),
            actualJdkVersionTrace.asBuildProblemSource(),
        ).filterIsInstance<FileBuildProblemSource>(),
        groupingMessage = message,
    )
}
