/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.tasks.jvm

import org.jetbrains.amper.BuildPrimitives
import org.jetbrains.amper.core.extract.cleanDirectory
import org.jetbrains.amper.engine.TaskGraphExecutionContext
import org.jetbrains.amper.frontend.Fragment
import org.jetbrains.amper.frontend.TaskName
import org.jetbrains.amper.incrementalcache.ExecuteOnChangedInputs
import org.jetbrains.amper.tasks.AdditionalResourcesProvider
import org.jetbrains.amper.tasks.TaskOutputRoot
import org.jetbrains.amper.tasks.TaskResult
import org.jetbrains.amper.tasks.artifacts.ArtifactTaskBase
import org.jetbrains.amper.tasks.artifacts.Selectors
import org.jetbrains.amper.tasks.artifacts.api.Quantifier
import org.jetbrains.amper.tasks.compose.PreparedComposeResourcesDirArtifact
import kotlin.io.path.createDirectories
import kotlin.io.path.deleteRecursively
import kotlin.io.path.div
import kotlin.io.path.isDirectory

/**
 * Provides prepared Compose Resources as java resources to be placed into the classpath.
 *
 * **Output**: [AdditionalResourcesProvider]
 */
class JvmComposeResourcesTask(
    override val taskName: TaskName,
    private val fragment: Fragment,
    private val taskOutputRoot: TaskOutputRoot,
    private val executeOnChangedInputs: ExecuteOnChangedInputs,
) : ArtifactTaskBase() {
    private val preparedResources by Selectors.fromFragment(
        type = PreparedComposeResourcesDirArtifact::class,
        fragment = fragment,
        quantifier = Quantifier.Single,
    )

    override suspend fun run(dependenciesResult: List<TaskResult>, executionContext: TaskGraphExecutionContext): TaskResult {
        val outputRoot = taskOutputRoot.path

        val dir = preparedResources.path
        if (!dir.isDirectory()) {
            outputRoot.deleteRecursively()
            return Result(emptyList())
        }

        executeOnChangedInputs.execute(taskName.name, emptyMap(), inputs = listOf(dir)) {
            cleanDirectory(outputRoot)
            val finalOutputDir = outputRoot / preparedResources.packagingDir

            // FIXME: Maybe don't copy the files,
            //  but introduce a `relativePackagingPath` for the `AdditionalResourcesProvider`?
            BuildPrimitives.copy(
                from = dir,
                to = finalOutputDir.createDirectories(),
            )

            ExecuteOnChangedInputs.ExecutionResult(
                outputs = listOf(finalOutputDir)
            )
        }

        return Result(
            resourceRoots = listOf(
                AdditionalResourcesProvider.ResourceRoot(
                    fragmentName = fragment.name,
                    path = outputRoot,
                ),
            ),
        )
    }

    private class Result(
        override val resourceRoots: List<AdditionalResourcesProvider.ResourceRoot>,
    ) : TaskResult, AdditionalResourcesProvider
}