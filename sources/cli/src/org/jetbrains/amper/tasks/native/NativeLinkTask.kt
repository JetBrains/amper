/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.tasks.native

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.jetbrains.amper.cli.AmperProjectTempRoot
import org.jetbrains.amper.cli.userReadableError
import org.jetbrains.amper.compilation.KotlinArtifactsDownloader
import org.jetbrains.amper.compilation.KotlinCompilationType
import org.jetbrains.amper.compilation.downloadCompilerPlugins
import org.jetbrains.amper.compilation.downloadNativeCompiler
import org.jetbrains.amper.compilation.kotlinNativeCompilerArgs
import org.jetbrains.amper.compilation.mergedKotlinSettings
import org.jetbrains.amper.core.AmperUserCacheRoot
import org.jetbrains.amper.core.UsedVersions
import org.jetbrains.amper.core.extract.cleanDirectory
import org.jetbrains.amper.frontend.AmperModule
import org.jetbrains.amper.frontend.Platform
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
    override val module: AmperModule,
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
    /**
     * Task names that produce klibs that need to be exposed as API in the resulting artifact.
     */
    val exportedKLibTaskNames: Set<TaskName>,
    private val kotlinArtifactsDownloader: KotlinArtifactsDownloader =
        KotlinArtifactsDownloader(userCacheRoot, executeOnChangedInputs),
): BuildTask {
    init {
        require(platform.isLeaf)
        require(platform.isDescendantOf(Platform.NATIVE))
        require(compilationType != KotlinCompilationType.LIBRARY)
    }

    override suspend fun run(dependenciesResult: List<TaskResult>): Result {
        val fragments = module.fragments.filter {
            it.platforms.contains(platform) && it.isTest == isTest
        }
        if (fragments.isEmpty()) {
            error("Zero fragments in module ${module.userReadableName} for platform $platform isTest=$isTest")
        }

        val externalKLibs = dependenciesResult
            .filterIsInstance<ResolveExternalDependenciesTask.Result>()
            .flatMap { it.compileClasspath } // runtime dependencies including transitive
            .distinct()
            .filter { !it.pathString.endsWith(".jar") }
            .toList()

        val includeArtifact = dependenciesResult
            .filterIsInstance<NativeCompileKlibTask.Result>()
            .firstOrNull { it.taskName == compileKLibTaskName }
            ?.compiledKlib
            ?: error("The result of the klib compilation task (${compileKLibTaskName.name}) was not found")

        val compileKLibDependencies = dependenciesResult
            .filterIsInstance<NativeCompileKlibTask.Result>()
            .filter { it.taskName != compileKLibTaskName }

        val exportedKLibDependencies = compileKLibDependencies
            .filter { it.taskName in exportedKLibTaskNames }
        check(exportedKLibDependencies.size == exportedKLibTaskNames.size)

        val compileKLibs = compileKLibDependencies.map { it.compiledKlib }
        val exportedKLibs = exportedKLibDependencies.map { it.compiledKlib }

        // TODO kotlin version settings
        val kotlinVersion = UsedVersions.kotlinVersion
        val kotlinUserSettings = fragments.mergedKotlinSettings()

        logger.info("native link '${module.userReadableName}' -- ${fragments.joinToString(" ") { it.name }}")

        val entryPoints = if (module.type.isApplication()) {
            fragments.mapNotNull { it.settings.native?.entryPoint }.distinct()
        } else emptyList()
        if (entryPoints.size > 1) {
            // TODO raise this error in the frontend?
            userReadableError("Multiple entry points defined in ${fragments.identificationPhrase()}:\n${entryPoints.joinToString("\n")}")
        }
        val entryPoint = entryPoints.singleOrNull()

        val configuration: Map<String, String> = mapOf(
            "kotlin.version" to kotlinVersion,
            "kotlin.settings" to Json.encodeToString(kotlinUserSettings),
            "entry.point" to (entryPoint ?: ""),
            "task.output.root" to taskOutputRoot.path.pathString,
        )

        val inputs = listOf(includeArtifact) + compileKLibs
        val artifact = executeOnChangedInputs.execute(taskName.name, configuration, inputs) {
            cleanDirectory(taskOutputRoot.path)

            val artifactPath = taskOutputRoot.path.resolve(compilationType.outputFilename(module, platform, isTest))

            val nativeCompiler = downloadNativeCompiler(kotlinVersion, userCacheRoot)
            val compilerPlugins = kotlinArtifactsDownloader.downloadCompilerPlugins(kotlinVersion, kotlinUserSettings)
            val args = kotlinNativeCompilerArgs(
                kotlinUserSettings = kotlinUserSettings,
                compilerPlugins = compilerPlugins,
                entryPoint = entryPoint,
                libraryPaths = compileKLibs + externalKLibs,
                exportedLibraryPaths = exportedKLibs,
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
