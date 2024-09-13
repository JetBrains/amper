/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.tasks.compose

import org.jetbrains.amper.core.extract.cleanDirectory
import org.jetbrains.amper.engine.Task
import org.jetbrains.amper.frontend.Fragment
import org.jetbrains.amper.frontend.TaskName
import org.jetbrains.amper.tasks.TaskOutputRoot
import org.jetbrains.amper.tasks.TaskResult
import org.jetbrains.compose.resources.prepareResources
import java.nio.file.Path

/**
 * See [prepareResources] step.
 */
class PrepareComposeResourcesTask(
    override val taskName: TaskName,
    private val fragment: Fragment,
    private val taskOutputRoot: TaskOutputRoot,
    private val originalResourcesDir: Path,
) : Task {
    override suspend fun run(dependenciesResult: List<TaskResult>): TaskResult {
        val outputDir = taskOutputRoot.path
            .apply(::cleanDirectory)

        prepareResources(
            qualifier = fragment.name,
            originalResourcesDir = originalResourcesDir,
            outputDirectory = outputDir,
        )
        return Result(outputDir)
    }

    class Result(
        val outputDir: Path,
    ) : TaskResult
}
