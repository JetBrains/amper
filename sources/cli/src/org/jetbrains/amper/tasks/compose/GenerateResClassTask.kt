/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.tasks.compose

import org.jetbrains.amper.cli.AmperBuildOutputRoot
import org.jetbrains.amper.core.extract.cleanDirectory
import org.jetbrains.amper.engine.Task
import org.jetbrains.amper.frontend.Fragment
import org.jetbrains.amper.frontend.TaskName
import org.jetbrains.amper.frontend.aomBuilder.composeResourcesGeneratedCommonResClassPath
import org.jetbrains.amper.tasks.AdditionalSourcesProvider
import org.jetbrains.amper.tasks.TaskResult
import org.jetbrains.amper.util.ExecuteOnChangedInputs
import org.jetbrains.compose.resources.generateResClass
import kotlin.io.path.pathString

/**
 * See [generateResClass] step.
 */
class GenerateResClassTask(
    override val taskName: TaskName,
    private val fragment: Fragment,
    private val packageName: String,
    private val makeAccessorsPublic: Boolean,
    private val packagingDir: String,
    private val buildOutputRoot: AmperBuildOutputRoot,
    private val executeOnChangedInputs: ExecuteOnChangedInputs,
) : Task {
    override suspend fun run(dependenciesResult: List<TaskResult>): TaskResult {
        val codeDir = fragment.composeResourcesGeneratedCommonResClassPath(buildOutputRoot.path)

        val config = mapOf(
            "packageName" to packageName,
            "packagingDir" to packagingDir,
            "isPublic" to makeAccessorsPublic.toString(),
            "outputSourceDirectory" to codeDir.pathString,
        )

        executeOnChangedInputs.execute(taskName.name, inputs = emptyList(), configuration = config) {
            cleanDirectory(codeDir)
            generateResClass(
                packageName = packageName,
                packagingDir = packagingDir,
                isPublic = makeAccessorsPublic,
                outputSourceDirectory = codeDir,
            )
            ExecuteOnChangedInputs.ExecutionResult(outputs = listOf(codeDir))
        }

        return Result(
            sourceRoots = listOf(
                AdditionalSourcesProvider.SourceRoot(fragment.name, codeDir),
            ),
        )
    }

    private class Result(
        override val sourceRoots: List<AdditionalSourcesProvider.SourceRoot>,
    ) : TaskResult, AdditionalSourcesProvider
}
