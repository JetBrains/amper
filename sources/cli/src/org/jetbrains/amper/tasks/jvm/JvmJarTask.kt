/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.tasks.jvm

import org.jetbrains.amper.engine.TaskName
import org.jetbrains.amper.frontend.PotatoModule
import org.jetbrains.amper.jar.JarConfig
import org.jetbrains.amper.jvm.findEffectiveJvmMainClass
import org.jetbrains.amper.tasks.JarTask
import org.jetbrains.amper.tasks.TaskOutputRoot
import org.jetbrains.amper.tasks.TaskResult
import org.jetbrains.amper.util.ExecuteOnChangedInputs
import java.nio.file.Path
import kotlin.io.path.div

class JvmJarTask(
    override val taskName: TaskName,
    private val module: PotatoModule,
    private val taskOutputRoot: TaskOutputRoot,
    executeOnChangedInputs: ExecuteOnChangedInputs,
) : JarTask(taskName, executeOnChangedInputs) {

    override fun getInputDirs(dependenciesResult: List<TaskResult>): List<Path> {
        val compileTaskResults = dependenciesResult.filterIsInstance<JvmCompileTask.TaskResult>()
        require(compileTaskResults.isNotEmpty()) {
            "Call Jar task without any compilation dependency"
        }
        return compileTaskResults.mapNotNull { it.classesOutputRoot }
    }

    override fun outputJarPath(): Path = taskOutputRoot.path / "${module.userReadableName}-jvm.jar"

    override fun jarConfig(): JarConfig = JarConfig(
        mainClassFqn = if (module.type.isApplication()) module.fragments.findEffectiveJvmMainClass() else null
    )
}
