/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.tasks.compose

import org.jetbrains.amper.cli.AmperBuildOutputRoot
import org.jetbrains.amper.core.extract.cleanDirectory
import org.jetbrains.amper.engine.Task
import org.jetbrains.amper.engine.requireSingleDependency
import org.jetbrains.amper.frontend.Fragment
import org.jetbrains.amper.frontend.TaskName
import org.jetbrains.amper.frontend.aomBuilder.composeResourcesGeneratedAccessorsPath
import org.jetbrains.amper.incrementalcache.ExecuteOnChangedInputs
import org.jetbrains.amper.tasks.AdditionalSourcesProvider
import org.jetbrains.amper.tasks.TaskResult
import org.jetbrains.compose.resources.generateResourceAccessors
import kotlin.io.path.deleteRecursively
import kotlin.io.path.pathString

/**
 * See [generateResourceAccessors] step.
 */
class GenerateResourceAccessorsTask(
    override val taskName: TaskName,
    private val fragment: Fragment,
    private val packageName: String,
    private val makeAccessorsPublic: Boolean,
    private val buildOutputRoot: AmperBuildOutputRoot,
    private val executeOnChangedInputs: ExecuteOnChangedInputs,
) : Task {
    override suspend fun run(dependenciesResult: List<TaskResult>): TaskResult {
        val codeDir = fragment.composeResourcesGeneratedAccessorsPath(buildOutputRoot.path)

        val prepareResult = when (val r = dependenciesResult.requireSingleDependency<PrepareComposeResourcesResult>()) {
            PrepareComposeResourcesResult.NoResources -> {
                codeDir.deleteRecursively()
                return Result(emptyList())
            }
            is PrepareComposeResourcesResult.Prepared -> r
        }

        val config = mapOf(
            "packageName" to packageName,
            // "qualifier" - fragment can't change
            "makeAccessorsPublic" to makeAccessorsPublic.toString(),
            "packagingDir" to prepareResult.relativePackagingPath,
            // "preparedResourcesDirectory" - already in inputs
            "outputSourceDirectory" to codeDir.pathString,
        )

        executeOnChangedInputs.execute(taskName.name, inputs = listOf(prepareResult.outputDir), configuration = config) {
            cleanDirectory(codeDir)
            generateResourceAccessors(
                packageName = packageName,
                qualifier = fragment.name,
                makeAccessorsPublic = makeAccessorsPublic,
                packagingDir = prepareResult.relativePackagingPath,
                preparedResourcesDirectory = prepareResult.outputDir,
                outputSourceDirectory = codeDir,
            )
            ExecuteOnChangedInputs.ExecutionResult(listOf(codeDir))
        }

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