/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.tasks.jvm

import org.jetbrains.amper.BuildPrimitives
import org.jetbrains.amper.core.extract.cleanDirectory
import org.jetbrains.amper.engine.Task
import org.jetbrains.amper.engine.requireSingleDependency
import org.jetbrains.amper.frontend.Fragment
import org.jetbrains.amper.frontend.TaskName
import org.jetbrains.amper.tasks.AdditionalResourcesProvider
import org.jetbrains.amper.tasks.TaskOutputRoot
import org.jetbrains.amper.tasks.TaskResult
import org.jetbrains.amper.tasks.compose.PrepareComposeResourcesTask
import kotlin.io.path.createDirectories
import kotlin.io.path.div

/**
 * Provides prepared Compose Resources as java resources to be placed into the classpath.
 *
 * **Inputs**:
 * - [PrepareComposeResourcesTask.Result]
 *
 * **Output**: [AdditionalResourcesProvider]
 */
class JvmComposeResourcesTask(
    override val taskName: TaskName,
    private val fragment: Fragment,
    private val taskOutputRoot: TaskOutputRoot,
) : Task {
    override suspend fun run(dependenciesResult: List<TaskResult>): TaskResult {
        val prepareResources = dependenciesResult.requireSingleDependency<PrepareComposeResourcesTask.Result>()

        val outputRoot = taskOutputRoot.path.apply(::cleanDirectory)
        val finalOutputDir = outputRoot / prepareResources.relativePackagingPath.replace("/", outputRoot.fileSystem.separator)

        // FIXME: Maybe don't copy the files,
        //  but introduce a `relativePackagingPath` for the `AdditionalResourcesProvider`?
        BuildPrimitives.copy(
            from = prepareResources.outputDir,
            to = finalOutputDir.createDirectories(),
        )

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