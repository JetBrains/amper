/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.tasks

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.jetbrains.amper.engine.Task
import org.jetbrains.amper.frontend.TaskName
import org.jetbrains.amper.incrementalcache.ExecuteOnChangedInputs
import org.jetbrains.amper.jar.JarConfig
import org.jetbrains.amper.jar.ZipInput
import org.jetbrains.amper.jar.writeJar
import java.nio.file.Path
import kotlin.io.path.createParentDirectories
import kotlin.io.path.pathString

abstract class AbstractJarTask(
    override val taskName: TaskName,
    private val executeOnChangedInputs: ExecuteOnChangedInputs,
) : Task {

    protected abstract suspend fun getInputDirs(dependenciesResult: List<TaskResult>): List<ZipInput>
    protected abstract fun outputJarPath(): Path
    protected abstract fun jarConfig(): JarConfig

    protected abstract fun createResult(jarPath: Path): Result

    override suspend fun run(dependenciesResult: List<TaskResult>): TaskResult {
        val inputDirs = getInputDirs(dependenciesResult)
        val outputJarPath = outputJarPath()

        val jarConfig = jarConfig()

        val configuration: Map<String, String> = mapOf(
            "jarConfig" to Json.encodeToString(jarConfig),
            "inputDirsDestPaths" to inputDirs.map { it.destPathInArchive }.toString(),
            "outputJarPath" to outputJarPath.pathString,
        )

        executeOnChangedInputs.execute(taskName.name, configuration, inputDirs.map { it.path }) {
            outputJarPath.createParentDirectories().writeJar(inputDirs, jarConfig)
            ExecuteOnChangedInputs.ExecutionResult(outputs = listOf(outputJarPath))
        }
        return createResult(outputJarPath)
    }

    abstract class Result(
        val jarPath: Path,
    ) : TaskResult
}
