/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.tasks.compose

import org.jetbrains.amper.cli.AmperBuildOutputRoot
import org.jetbrains.amper.core.extract.cleanDirectory
import org.jetbrains.amper.engine.Task
import org.jetbrains.amper.frontend.Fragment
import org.jetbrains.amper.frontend.TaskName
import org.jetbrains.amper.frontend.aomBuilder.composeResourcesGeneratedCollectorsPath
import org.jetbrains.amper.tasks.AdditionalSourcesProvider
import org.jetbrains.amper.tasks.TaskResult
import org.jetbrains.amper.util.ExecuteOnChangedInputs
import org.jetbrains.compose.resources.generateExpectResourceCollectors
import kotlin.io.path.deleteRecursively
import kotlin.io.path.pathString

/**
 * See [generateExpectResourceCollectors] dir.
 */
class GenerateExpectResourceCollectorsTask(
    override val taskName: TaskName,
    private val shouldGenerateCode: () -> Boolean,
    private val fragment: Fragment,
    private val packageName: String,
    private val makeAccessorsPublic: Boolean,
    private val buildOutputRoot: AmperBuildOutputRoot,
    private val executeOnChangedInputs: ExecuteOnChangedInputs,
) : Task {
    override suspend fun run(dependenciesResult: List<TaskResult>): TaskResult {
        val codeDir = fragment.composeResourcesGeneratedCollectorsPath(buildOutputRoot.path)

        if (!shouldGenerateCode()) {
            codeDir.deleteRecursively()
            return Result(emptyList())
        }

        val config = mapOf(
            "packageName" to packageName,
            "makeAccessorsPublic" to makeAccessorsPublic.toString(),
            "outputSourceDirectory" to codeDir.pathString,
        )
        executeOnChangedInputs.execute(taskName.name, inputs = emptyList(), configuration = config) {
            cleanDirectory(codeDir)
            generateExpectResourceCollectors(
                packageName = packageName,
                makeAccessorsPublic = makeAccessorsPublic,
                outputSourceDirectory = codeDir,
            )
            ExecuteOnChangedInputs.ExecutionResult(outputs = listOf(codeDir))
        }

        return Result(
            sourceRoots = listOf(
                AdditionalSourcesProvider.SourceRoot(
                    fragmentName = fragment.name,
                    path = codeDir,
                ),
            ),
        )
    }

    private class Result(
        override val sourceRoots: List<AdditionalSourcesProvider.SourceRoot>,
    ) : TaskResult, AdditionalSourcesProvider
}