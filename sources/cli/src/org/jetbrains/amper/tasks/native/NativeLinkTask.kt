/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.tasks.native

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.jetbrains.amper.cli.AmperProjectTempRoot
import org.jetbrains.amper.cli.AmperUserCacheRoot
import org.jetbrains.amper.compilation.KotlinCompilationType
import org.jetbrains.amper.compilation.KotlinCompilerDownloader
import org.jetbrains.amper.compilation.downloadCompilerPlugins
import org.jetbrains.amper.compilation.downloadNativeCompiler
import org.jetbrains.amper.compilation.kotlinNativeCompilerArgs
import org.jetbrains.amper.compilation.mergedKotlinSettings
import org.jetbrains.amper.core.UsedVersions
import org.jetbrains.amper.core.extract.cleanDirectory
import org.jetbrains.amper.engine.TaskName
import org.jetbrains.amper.frontend.Platform
import org.jetbrains.amper.frontend.PotatoModule
import org.jetbrains.amper.frontend.isDescendantOf
import org.jetbrains.amper.tasks.BuildTask
import org.jetbrains.amper.tasks.CommonTaskUtils.userReadableList
import org.jetbrains.amper.tasks.TaskOutputRoot
import org.jetbrains.amper.util.ExecuteOnChangedInputs
import org.slf4j.LoggerFactory
import java.nio.file.Path
import kotlin.io.path.pathString

class NativeLinkTask(
    override val module: PotatoModule,
    override val platform: Platform,
    private val userCacheRoot: AmperUserCacheRoot,
    private val taskOutputRoot: TaskOutputRoot,
    private val executeOnChangedInputs: ExecuteOnChangedInputs,
    override val taskName: TaskName,
    private val tempRoot: AmperProjectTempRoot,
    override val isTest: Boolean,
    val compilationType: KotlinCompilationType,
    /**
     * The name of the task that produces the klib for the sources of this module.
     */
    val compileKLibTaskName: TaskName,
    private val kotlinCompilerDownloader: KotlinCompilerDownloader =
        KotlinCompilerDownloader(userCacheRoot, executeOnChangedInputs),
): BuildTask {
    init {
        require(platform.isLeaf)
        require(platform.isDescendantOf(Platform.NATIVE))
    }

    override suspend fun run(dependenciesResult: List<org.jetbrains.amper.tasks.TaskResult>): TaskResult {
        val fragments = module.fragments.filter {
            it.platforms.contains(platform) && it.isTest == isTest
        }
        if (fragments.isEmpty()) {
            error("Zero fragments in module ${module.userReadableName} for platform $platform isTest=$isTest")
        }

        val moduleKLibCompilationResult = dependenciesResult
            .filterIsInstance<NativeCompileKlibTask.TaskResult>()
            .firstOrNull { it.taskName == compileKLibTaskName }
            ?: error("The result of the klib compilation task (${compileKLibTaskName.name}) was not found")
        val includeArtifact = moduleKLibCompilationResult.compiledKlib

        // TODO kotlin version settings
        val kotlinVersion = UsedVersions.kotlinVersion
        val kotlinUserSettings = fragments.mergedKotlinSettings()

        logger.info("native link '${module.userReadableName}' -- ${fragments.joinToString(" ") { it.name }}")

        val entryPoints = if (module.type.isApplication()) {
            fragments.mapNotNull { it.settings.native?.entryPoint }.distinct()
        } else emptyList()
        if (entryPoints.size > 1) {
            error("Multiple entry points defined for module ${module.userReadableName} fragments ${fragments.userReadableList()}: ${entryPoints.joinToString()}")
        }
        val entryPoint = entryPoints.singleOrNull()

        val configuration: Map<String, String> = mapOf(
            "kotlin.version" to kotlinVersion,
            "kotlin.settings" to Json.encodeToString(kotlinUserSettings),
            "entry.point" to (entryPoint ?: ""),
            "task.output.root" to taskOutputRoot.path.pathString,
        )

        val inputs = listOf(includeArtifact) + moduleKLibCompilationResult.dependencyKlibs
        val artifact = executeOnChangedInputs.execute(taskName.name, configuration, inputs) {
            cleanDirectory(taskOutputRoot.path)

            val artifactPath = taskOutputRoot.path.resolve(compilationType.outputFilename(module, platform, isTest))

            val nativeCompiler = kotlinCompilerDownloader.downloadNativeCompiler(kotlinVersion)
            val compilerPlugins = kotlinCompilerDownloader.downloadCompilerPlugins(kotlinVersion, kotlinUserSettings)
            val args = kotlinNativeCompilerArgs(
                kotlinUserSettings = kotlinUserSettings,
                compilerPlugins = compilerPlugins,
                entryPoint = entryPoint,
                libraryPaths = moduleKLibCompilationResult.dependencyKlibs,
                // no need to pass fragments nor sources, we only build from klibs
                fragments = emptyList(),
                sourceFiles = emptyList(),
                outputPath = artifactPath,
                compilationType = compilationType,
                include = includeArtifact,
            )

            nativeCompiler.compile(args, tempRoot, module)

            return@execute ExecuteOnChangedInputs.ExecutionResult(listOf(artifactPath))
        }.outputs.single()

        return TaskResult(
            dependencies = dependenciesResult,
            linkedBinary = artifact,
        )
    }

    class TaskResult(
        override val dependencies: List<org.jetbrains.amper.tasks.TaskResult>,
        val linkedBinary: Path,
    ) : org.jetbrains.amper.tasks.TaskResult

    private val logger = LoggerFactory.getLogger(javaClass)
}
