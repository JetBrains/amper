/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.tasks

import org.jetbrains.amper.BuildPrimitives
import org.jetbrains.amper.cli.AmperProjectRoot
import org.jetbrains.amper.cli.TaskName
import org.jetbrains.amper.diagnostics.spanBuilder
import org.jetbrains.amper.diagnostics.useWithScope
import org.jetbrains.amper.frontend.PotatoModule
import org.jetbrains.amper.frontend.PotatoModuleFileSource
import org.jetbrains.amper.frontend.PotatoModuleProgrammaticSource
import kotlin.io.path.pathString

class NativeTestTask(
    override val taskName: TaskName,
    private val module: PotatoModule,
    private val projectRoot: AmperProjectRoot,
) : Task {
    override suspend fun run(dependenciesResult: List<TaskResult>): TaskResult {
        val compileTaskResult = dependenciesResult.filterIsInstance<NativeCompileTask.TaskResult>().singleOrNull()
            ?: error("Could not find a single compile task in dependencies of $taskName")

        val executable = compileTaskResult.artifact

        return spanBuilder("native-test")
            .setAttribute("executable", executable.pathString)
            .useWithScope { span ->
                val workingDir = when (val source = module.source) {
                    is PotatoModuleFileSource -> source.buildDir
                    PotatoModuleProgrammaticSource -> projectRoot.path
                }

                BuildPrimitives.runProcessAndAssertExitCode(listOf(executable.pathString), workingDir, span)

                object : TaskResult {
                    override val dependencies: List<TaskResult> = dependenciesResult
                }
            }
    }
}
