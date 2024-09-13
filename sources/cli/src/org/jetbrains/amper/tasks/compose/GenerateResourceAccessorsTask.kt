/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.tasks.compose

import org.jetbrains.amper.cli.AmperBuildOutputRoot
import org.jetbrains.amper.core.extract.cleanDirectory
import org.jetbrains.amper.engine.Task
import org.jetbrains.amper.frontend.Fragment
import org.jetbrains.amper.frontend.TaskName
import org.jetbrains.amper.frontend.aomBuilder.composeResourcesGeneratedAccessorsPath
import org.jetbrains.amper.tasks.AdditionalSourcesProvider
import org.jetbrains.amper.tasks.TaskResult
import org.jetbrains.compose.resources.generateResourceAccessors

/**
 * See [generateResourceAccessors] step.
 */
class GenerateResourceAccessorsTask(
    override val taskName: TaskName,
    private val fragment: Fragment,
    private val packageName: String,
    private val makeAccessorsPublic: Boolean,
    private val packagingDir: String,
    private val buildOutputRoot: AmperBuildOutputRoot,
) : Task {
    override suspend fun run(dependenciesResult: List<TaskResult>): TaskResult {
        val codeDir = fragment.composeResourcesGeneratedAccessorsPath(buildOutputRoot.path)
            .apply(::cleanDirectory)

        val preparedResourcesDirectory = dependenciesResult
            .filterIsInstance<PrepareComposeResourcesTask.Result>()
            .single().outputDir

        generateResourceAccessors(
            packageName = packageName,
            qualifier = fragment.name,
            makeAccessorsPublic = makeAccessorsPublic,
            packagingDir = packagingDir,
            preparedResourcesDirectory = preparedResourcesDirectory,
            outputSourceDirectory = codeDir,
        )

        return Result(
            sourceRoots = listOf(
                AdditionalSourcesProvider.SourceRoot(
                    fragmentName = fragment.name,
                    path = codeDir,
                )
            )
        )
    }

    internal class Result(
        override val sourceRoots: List<AdditionalSourcesProvider.SourceRoot>,
    ) : TaskResult, AdditionalSourcesProvider
}