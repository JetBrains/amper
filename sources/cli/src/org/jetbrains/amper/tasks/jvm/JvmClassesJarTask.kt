/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.tasks.jvm

import org.jetbrains.amper.engine.BuildTask
import org.jetbrains.amper.frontend.AmperModule
import org.jetbrains.amper.frontend.Platform
import org.jetbrains.amper.frontend.TaskName
import org.jetbrains.amper.incrementalcache.ExecuteOnChangedInputs
import org.jetbrains.amper.jar.JarConfig
import org.jetbrains.amper.jar.ZipInput
import org.jetbrains.amper.jvm.findEffectiveJvmMainClass
import org.jetbrains.amper.tasks.AbstractJarTask
import org.jetbrains.amper.tasks.TaskOutputRoot
import org.jetbrains.amper.tasks.TaskResult
import org.jetbrains.amper.util.BuildType
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.div

/**
 * Creates a jar file containing all the JVM classes produced by task dependencies of type [JvmCompileTask].
 */
class JvmClassesJarTask(
    override val taskName: TaskName,
    override val module: AmperModule,
    override val buildType: BuildType? = null,
    override val platform: Platform = Platform.JVM,
    private val taskOutputRoot: TaskOutputRoot,
    executeOnChangedInputs: ExecuteOnChangedInputs,
) : AbstractJarTask(taskName, executeOnChangedInputs), BuildTask {

    override val isTest: Boolean
        get() = false

    init {
        require(platform == Platform.JVM || platform == Platform.ANDROID) {
            "Illegal platform for JvmClassesJarTask: $platform"
        }
    }

    override fun getInputDirs(dependenciesResult: List<TaskResult>): List<ZipInput> {
        val compileTaskResults = dependenciesResult.filterIsInstance<JvmCompileTask.Result>()
        require(compileTaskResults.isNotEmpty()) {
            "Call Jar task without any compilation dependency"
        }
        return compileTaskResults.map { ZipInput(path = it.classesOutputRoot, destPathInArchive = Path(".")) }
    }

    // TODO add version here?
    override fun outputJarPath(): Path {
        return taskOutputRoot.path / "${module.userReadableName}-jvm.jar"
    }

    override fun jarConfig(): JarConfig = JarConfig(mainClassFqn = findMainClass())

    private fun findMainClass(): String? {
        if (!module.type.isApplication()) {
            return null
        }
        // We don't want to fail here, because Android applications don't have a main function and that's normal
        return module.fragments.filter { it.isTest == isTest }.findEffectiveJvmMainClass()
    }

    override fun createResult(jarPath: Path): AbstractJarTask.Result = Result(jarPath)

    class Result(jarPath: Path) : AbstractJarTask.Result(jarPath), RuntimeClasspathElementProvider {
        override val paths: List<Path>
            get() = listOf(jarPath)
    }
}
