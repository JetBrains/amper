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
import org.jetbrains.compose.resources.generateActualResourceCollectors

/**
 * See [generateActualResourceCollectors] step.
 */
class GenerateActualResourceCollectorsTask(
    override val taskName: TaskName,
    private val fragment: Fragment,
    private val packageName: String,
    private val makeAccessorsPublic: Boolean,
    private val buildOutputRoot: AmperBuildOutputRoot,
    private val useActualModifier: Boolean,
) : Task {
    override suspend fun run(dependenciesResult: List<TaskResult>): TaskResult {
        val resourceAccessorDirs = dependenciesResult
            .filterIsInstance<GenerateResourceAccessorsTask.Result>()
            .flatMap { result -> result.sourceRoots.map { it.path } }

        if (resourceAccessorDirs.isEmpty()) {
            return Result(emptyList())
        }

        val codeDir = fragment.composeResourcesGeneratedCollectorsPath(buildOutputRoot.path)
            .apply(::cleanDirectory)

        generateActualResourceCollectors(
            packageName = packageName,
            makeAccessorsPublic = makeAccessorsPublic,
            accessorDirectories = resourceAccessorDirs,
            outputSourceDirectory = codeDir,
            useActualModifier = useActualModifier,
        )

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
