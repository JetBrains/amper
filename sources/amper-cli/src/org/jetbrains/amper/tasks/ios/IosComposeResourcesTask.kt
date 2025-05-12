/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.tasks.ios

import org.jetbrains.amper.BuildPrimitives
import org.jetbrains.amper.core.AmperUserCacheRoot
import org.jetbrains.amper.core.extract.cleanDirectory
import org.jetbrains.amper.engine.TaskGraphExecutionContext
import org.jetbrains.amper.frontend.LeafFragment
import org.jetbrains.amper.frontend.TaskName
import org.jetbrains.amper.incrementalcache.ExecuteOnChangedInputs
import org.jetbrains.amper.tasks.EmptyTaskResult
import org.jetbrains.amper.tasks.TaskOutputRoot
import org.jetbrains.amper.tasks.TaskResult
import org.jetbrains.amper.tasks.artifacts.ArtifactTaskBase
import org.jetbrains.amper.tasks.artifacts.Selectors
import org.jetbrains.amper.tasks.artifacts.api.Quantifier
import org.jetbrains.amper.tasks.compose.MergedPreparedComposeResourcesDirArtifact
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.deleteRecursively
import kotlin.io.path.div
import kotlin.io.path.isDirectory

/**
 * Assembles all the required resources to be later packaged into the iOS app.
 */
class IosComposeResourcesTask(
    override val taskName: TaskName,
    private val leafFragment: LeafFragment,
    private val taskOutputRoot: TaskOutputRoot,
    private val executeOnChangedInputs: ExecuteOnChangedInputs,
    userCacheRoot: AmperUserCacheRoot,
) : ArtifactTaskBase() {
    private val dependenciesMerged by Selectors.fromModuleWithDependencies(
        type = MergedPreparedComposeResourcesDirArtifact::class,
        leafFragment = leafFragment,
        userCacheRoot = userCacheRoot,
        quantifier = Quantifier.AtLeastOne,
    )

    override suspend fun run(dependenciesResult: List<TaskResult>, executionContext: TaskGraphExecutionContext): TaskResult {
        val results = dependenciesMerged.filter { it.path.isDirectory() }
        val outputPath = taskOutputRoot.path / "merged"
        if (results.isEmpty()) {
            outputPath.deleteRecursively()
            return EmptyTaskResult
        }

        executeOnChangedInputs.execute(
            id = taskName.name,
            configuration = emptyMap(),
            inputs = results.map { it.path },
        ) {
            cleanDirectory(outputPath)
            results.forEach { result ->
                BuildPrimitives.copy(
                    from = result.path,
                    to = outputPath.createDirectories(),
                )
            }
            ExecuteOnChangedInputs.ExecutionResult(listOf(outputPath))
        }

        return Result(composeResourcesDirectory = outputPath)
    }

    class Result(
        val composeResourcesDirectory: Path,
    ) : TaskResult
}