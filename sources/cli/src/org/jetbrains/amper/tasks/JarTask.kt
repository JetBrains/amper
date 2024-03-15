/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.tasks

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.jetbrains.amper.engine.Task
import org.jetbrains.amper.engine.TaskName
import org.jetbrains.amper.jar.JarConfig
import org.jetbrains.amper.jar.writeJar
import org.jetbrains.amper.util.ExecuteOnChangedInputs
import java.nio.file.Path
import kotlin.io.path.createParentDirectories
import kotlin.io.path.pathString

abstract class JarTask(
    override val taskName: TaskName,
    private val executeOnChangedInputs: ExecuteOnChangedInputs,
) : Task {

    protected abstract fun getInputDirs(dependenciesResult: List<TaskResult>): List<Path>

    protected abstract fun outputJarPath(): Path

    protected abstract fun jarConfig(): JarConfig

    override suspend fun run(dependenciesResult: List<TaskResult>): TaskResult {
        val inputDirs = getInputDirs(dependenciesResult)
        val outputJarPath = outputJarPath()

        val jarConfig = jarConfig()

        val configuration: Map<String, String> = mapOf(
            "jarConfig" to Json.encodeToString(jarConfig),
            "outputJarPath" to outputJarPath.pathString,
        )
        executeOnChangedInputs.execute(taskName.name, configuration, inputDirs) {
            outputJarPath.createParentDirectories().writeJar(inputDirs, jarConfig)
            ExecuteOnChangedInputs.ExecutionResult(outputs = listOf(outputJarPath))
        }
        return Result(dependenciesResult, outputJarPath)
    }

    class Result(
        override val dependencies: List<TaskResult>,
        val jarPath: Path,
    ) : TaskResult
}
