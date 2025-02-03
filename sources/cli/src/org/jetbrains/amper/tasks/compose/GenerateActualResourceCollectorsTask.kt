/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.tasks.compose

import org.jetbrains.amper.cli.AmperBuildOutputRoot
import org.jetbrains.amper.core.extract.cleanDirectory
import org.jetbrains.amper.engine.Task
import org.jetbrains.amper.frontend.LeafFragment
import org.jetbrains.amper.frontend.TaskName
import org.jetbrains.amper.frontend.aomBuilder.composeResourcesGeneratedCollectorsPath
import org.jetbrains.amper.incrementalcache.ExecuteOnChangedInputs
import org.jetbrains.amper.tasks.AdditionalSourcesProvider
import org.jetbrains.amper.tasks.TaskResult
import org.jetbrains.compose.resources.generateActualResourceCollectors
import kotlin.io.path.deleteRecursively
import kotlin.io.path.pathString

/**
 * See [generateActualResourceCollectors] step.
 */
class GenerateActualResourceCollectorsTask(
    override val taskName: TaskName,
    private val fragment: LeafFragment,
    private val packageName: String,
    private val shouldGenerateCode: () -> Boolean,
    private val buildOutputRoot: AmperBuildOutputRoot,
    private val executeOnChangedInputs: ExecuteOnChangedInputs,
    private val makeAccessorsPublic: Boolean,
    private val useActualModifier: Boolean,
) : Task {
    override suspend fun run(dependenciesResult: List<TaskResult>): TaskResult {
        val codeDir = fragment.composeResourcesGeneratedCollectorsPath(buildOutputRoot.path)

        if (!shouldGenerateCode()) {
            codeDir.deleteRecursively()
            return Result(emptyList())
        }

        val resourceAccessorDirs = dependenciesResult
            .filterIsInstance<GenerateResourceAccessorsTask.Result>()
            .flatMap { result -> result.sourceRoots.map { it.path } }

        val config = mapOf(
            "packageName" to packageName,
            "makeAccessorsPublic" to makeAccessorsPublic.toString(),
            "useActualModifier" to useActualModifier.toString(),
            "outputSourceDirectory" to codeDir.pathString,
        )
        executeOnChangedInputs.execute(taskName.name, inputs = resourceAccessorDirs, configuration = config) {
            cleanDirectory(codeDir)
            generateActualResourceCollectors(
                packageName = packageName,
                makeAccessorsPublic = makeAccessorsPublic,
                accessorDirectories = resourceAccessorDirs,
                outputSourceDirectory = codeDir,
                useActualModifier = useActualModifier,
            )
            ExecuteOnChangedInputs.ExecutionResult(
                outputs = listOf(codeDir),
            )
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
