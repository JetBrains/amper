/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.tasks.jvm

import nl.adaptivity.xmlutil.core.impl.multiplatform.name
import org.jetbrains.amper.core.AmperUserCacheRoot
import org.jetbrains.amper.engine.PackageTask
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
import org.jetbrains.amper.tasks.executable.ExecutableJarAssembler
import org.jetbrains.amper.tasks.executable.ExecutableJarConfig
import org.jetbrains.amper.util.BuildType
import java.nio.file.Path
import kotlin.io.path.div

/**
 * Task that creates an executable jar.
 */
class ExecutableJarTask(
    override val taskName: TaskName,
    override val module: AmperModule,
    val executeOnChangedInputs: ExecuteOnChangedInputs,
    val userCacheRoot: AmperUserCacheRoot,
    private val taskOutputRoot: TaskOutputRoot,
    private val outputJarName: String = "${module.userReadableName}-jvm-executable.jar"
) : AbstractJarTask(taskName, executeOnChangedInputs), PackageTask {

    private val assembler = ExecutableJarAssembler(userCacheRoot, executeOnChangedInputs)

    override suspend fun getInputDirs(dependenciesResult: List<TaskResult>): List<ZipInput> {
        val compiledClasses = dependenciesResult
            .filterIsInstance<JvmCompileTask.Result>()
            .map { it.classesOutputRoot }

        val runtimeDependencies = dependenciesResult
            .filterIsInstance<JvmRuntimeClasspathTask.Result>()
            .flatMap { it.jvmRuntimeClasspath }

        val config = createExecutableConfig()
        return with(assembler) { config.prepareJarInputs(compiledClasses, runtimeDependencies) }
    }

    override fun outputJarPath(): Path = taskOutputRoot.path / outputJarName

    override fun jarConfig(): JarConfig = with(assembler) { createExecutableConfig().createJarConfig() }

    private fun createExecutableConfig(): ExecutableJarConfig {
        require(module.type.isApplication()) { "Illegal module type for ${ExecutableJarTask::class.name}: ${module.type}" }
        val mainClass = module.fragments.filter { !it.isTest }.findEffectiveJvmMainClass()
        return ExecutableJarConfig(mainClassName = mainClass, additionalManifestProperties = emptyMap())
    }

    override fun createResult(jarPath: Path): AbstractJarTask.Result = Result(jarPath)

    override val platform: Platform
        get() = Platform.JVM

    override val buildType: BuildType
        get() = BuildType.Release

    override val format: PackageTask.Format
        get() = PackageTask.Format.ExecutableJar

    class Result(jarPath: Path) : AbstractJarTask.Result(jarPath) {
        val paths: List<Path>
            get() = listOf(jarPath)
    }
}
