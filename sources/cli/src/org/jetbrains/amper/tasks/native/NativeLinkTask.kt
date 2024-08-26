/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.tasks.native

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.jetbrains.amper.cli.AmperProjectTempRoot
import org.jetbrains.amper.compilation.KotlinArtifactsDownloader
import org.jetbrains.amper.compilation.KotlinCompilationType
import org.jetbrains.amper.compilation.downloadCompilerPlugins
import org.jetbrains.amper.compilation.downloadNativeCompiler
import org.jetbrains.amper.compilation.kotlinNativeCompilerArgs
import org.jetbrains.amper.compilation.mergedKotlinSettings
import org.jetbrains.amper.core.AmperUserCacheRoot
import org.jetbrains.amper.core.UsedVersions
import org.jetbrains.amper.core.extract.cleanDirectory
import org.jetbrains.amper.frontend.Platform
import org.jetbrains.amper.frontend.PotatoModule
import org.jetbrains.amper.frontend.TaskName
import org.jetbrains.amper.frontend.isDescendantOf
import org.jetbrains.amper.tasks.BuildTask
import org.jetbrains.amper.tasks.ResolveExternalDependenciesTask
import org.jetbrains.amper.tasks.TaskOutputRoot
import org.jetbrains.amper.tasks.TaskResult
import org.jetbrains.amper.tasks.identificationPhrase
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
    private val kotlinArtifactsDownloader: KotlinArtifactsDownloader =
        KotlinArtifactsDownloader(userCacheRoot, executeOnChangedInputs),
): BuildTask {
    init {
        require(platform.isLeaf)
        require(platform.isDescendantOf(Platform.NATIVE))
    }

    override suspend fun run(dependenciesResult: List<TaskResult>): Result {
        val fragments = module.fragments.filter {
            it.platforms.contains(platform) && it.isTest == isTest
        }
        if (fragments.isEmpty()) {
            error("Zero fragments in module ${module.userReadableName} for platform $platform isTest=$isTest")
        }

        val externalDependencies = dependenciesResult
            .filterIsInstance<ResolveExternalDependenciesTask.Result>()
            .flatMap { it.compileClasspath } // compiler dependencies including transitive
            .distinct()
            .filter { !it.pathString.endsWith(".jar") }
            .toList()

        val moduleKLibCompilationResult = dependenciesResult
            .filterIsInstance<NativeCompileKlibTask.Result>()
            .firstOrNull { it.taskName == compileKLibTaskName }
            ?: error("The result of the klib compilation task (${compileKLibTaskName.name}) was not found")
        val includeArtifact = moduleKLibCompilationResult.compiledKlib

        val compileKLibDependencies = dependenciesResult
            .filterIsInstance<NativeCompileKlibTask.Result>()
            .filter { it.taskName != compileKLibTaskName }

        // TODO kotlin version settings
        val kotlinVersion = UsedVersions.kotlinVersion
        val kotlinUserSettings = fragments.mergedKotlinSettings()

        logger.info("native link '${module.userReadableName}' -- ${fragments.joinToString(" ") { it.name }}")

        val entryPoints = if (module.type.isApplication()) {
            fragments.mapNotNull { it.settings.native?.entryPoint }.distinct()
        } else emptyList<String>()
        if (entryPoints.size > 1) {
            error("Multiple entry points defined in ${fragments.identificationPhrase()}: ${entryPoints.joinToString()}")
        }
        val entryPoint = entryPoints.singleOrNull()

        val configuration: Map<String, String> = mapOf(
            "kotlin.version" to kotlinVersion,
            "kotlin.settings" to Json.encodeToString(kotlinUserSettings),
            "entry.point" to (entryPoint ?: ""),
            "task.output.root" to taskOutputRoot.path.pathString,
        )

        val inputs = listOf(includeArtifact) + compileKLibDependencies.map { it.compiledKlib }
        val artifact = executeOnChangedInputs.execute(taskName.name, configuration, inputs) {
            cleanDirectory(taskOutputRoot.path)

            val artifactPath = taskOutputRoot.path.resolve(compilationType.outputFilename(module, platform, isTest))

            val nativeCompiler = downloadNativeCompiler(kotlinVersion, userCacheRoot)
            val compilerPlugins = kotlinArtifactsDownloader.downloadCompilerPlugins(kotlinVersion, kotlinUserSettings)
            val args = kotlinNativeCompilerArgs(
                kotlinUserSettings = kotlinUserSettings,
                compilerPlugins = compilerPlugins,
                entryPoint = entryPoint,
                libraryPaths = compileKLibDependencies.map { it.compiledKlib } + externalDependencies,
                // no need to pass fragments nor sources, we only build from klibs
                fragments = emptyList(),
                sourceFiles = emptyList(),
                additionalSourceRoots = emptyList(),
                outputPath = artifactPath,
                compilationType = compilationType,
                include = includeArtifact,
            )

            nativeCompiler.compile(args, tempRoot, module)

            return@execute ExecuteOnChangedInputs.ExecutionResult(listOf(artifactPath))
        }.outputs.single()

        return Result(
            linkedBinary = artifact,
        )
    }

    class Result(
        val linkedBinary: Path,
    ) : TaskResult

    private val logger = LoggerFactory.getLogger(javaClass)
}
