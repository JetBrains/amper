/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.diagnostics

import org.jetbrains.amper.frontend.AmperModule
import org.jetbrains.amper.frontend.SchemaBundle
import org.jetbrains.amper.frontend.api.Trace
import org.jetbrains.amper.frontend.api.schemaDelegate
import org.jetbrains.amper.frontend.asBuildProblemSource
import org.jetbrains.amper.problems.reporting.BuildProblem
import org.jetbrains.amper.problems.reporting.BuildProblemId
import org.jetbrains.amper.problems.reporting.BuildProblemSource
import org.jetbrains.amper.problems.reporting.BuildProblemType
import org.jetbrains.amper.problems.reporting.FileBuildProblemSource
import org.jetbrains.amper.problems.reporting.Level
import org.jetbrains.amper.problems.reporting.MultipleLocationsBuildProblemSource
import org.jetbrains.amper.problems.reporting.ProblemReporter

private const val MinJavaVersionForJIC = 16

object JavaIncrementalCompilationRequiresJava16Factory : AomSingleModuleDiagnosticFactory {
    override val diagnosticId: BuildProblemId
        get() = "java.incremental.compilation.requires.higher.jdk"

    override fun analyze(module: AmperModule, problemReporter: ProblemReporter) {
        val reportedPlaces = mutableSetOf<Pair<Trace, Trace>>()
        module.fragments.forEach { fragment ->
            val compileIncrementally = fragment.settings.java.compileIncrementally
            if (compileIncrementally && fragment.settings.jvm.jdk.version < MinJavaVersionForJIC) {
                val alreadyReported = !reportedPlaces.add(Pair(
                    fragment.settings.java::compileIncrementally.schemaDelegate.trace,
                    fragment.settings.jvm.jdk::version.schemaDelegate.trace,
                ))
                if (!alreadyReported) {
                    problemReporter.reportMessage(
                        JavaIncrementalCompilationRequiresJava16(
                            incrementalCompilationTrace = fragment.settings.java::compileIncrementally.schemaDelegate.trace,
                            actualJdkVersion = fragment.settings.jvm.jdk.version,
                            actualJdkVersionTrace = fragment.settings.jvm.jdk::version.schemaDelegate.trace,
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
    override val buildProblemId = JavaIncrementalCompilationRequiresJava16Factory.diagnosticId
    override val message = SchemaBundle.message(buildProblemId, minJdkVersion, actualJdkVersion)
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
