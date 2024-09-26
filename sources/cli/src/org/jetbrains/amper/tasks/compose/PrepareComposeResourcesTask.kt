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
import org.jetbrains.amper.util.ExecuteOnChangedInputs
import org.jetbrains.compose.resources.prepareResources
import java.nio.file.Path
import kotlin.io.path.pathString

/**
 * See [prepareResources] step.
 */
class PrepareComposeResourcesTask(
    override val taskName: TaskName,
    private val fragment: Fragment,
    private val packagingDir: String,
    private val originalResourcesDir: Path,
    private val taskOutputRoot: TaskOutputRoot,
    private val executeOnChangedInputs: ExecuteOnChangedInputs,
) : Task {
    override suspend fun run(dependenciesResult: List<TaskResult>): TaskResult {
        val outputDir = taskOutputRoot.path

        val config = mapOf(
            // "qualifier" - doesn't change
            // "originalResourcesDir" - inputs
            "outputDirectory" to outputDir.pathString,
        )
        executeOnChangedInputs.execute(taskName.name, inputs = listOf(originalResourcesDir), configuration = config) {
            cleanDirectory(outputDir)
            prepareResources(
                qualifier = fragment.name,
                originalResourcesDir = originalResourcesDir,
                outputDirectory = outputDir,
            )
            ExecuteOnChangedInputs.ExecutionResult(outputs = listOf(outputDir))
        }
        return Result(
            outputDir = outputDir,
            relativePackagingPath = packagingDir,
        )
    }

    class Result(
        val outputDir: Path,
        val relativePackagingPath: String,
    ) : TaskResult
}
