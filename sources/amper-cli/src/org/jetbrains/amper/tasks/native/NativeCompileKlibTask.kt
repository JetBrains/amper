/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.tasks.native

import kotlinx.serialization.json.Json
import org.jetbrains.amper.cli.AmperProjectTempRoot
import org.jetbrains.amper.compilation.KotlinArtifactsDownloader
import org.jetbrains.amper.compilation.KotlinCompilationType
import org.jetbrains.amper.compilation.downloadCompilerPlugins
import org.jetbrains.amper.compilation.downloadNativeCompiler
import org.jetbrains.amper.compilation.kotlinNativeCompilerArgs
import org.jetbrains.amper.compilation.serializableKotlinSettings
import org.jetbrains.amper.compilation.singleLeafFragment
import org.jetbrains.amper.compilation.validSourceFileExtensions
import org.jetbrains.amper.core.AmperUserCacheRoot
import org.jetbrains.amper.core.UsedVersions
import org.jetbrains.amper.core.extract.cleanDirectory
import org.jetbrains.amper.engine.BuildTask
import org.jetbrains.amper.engine.TaskGraphExecutionContext
import org.jetbrains.amper.frontend.AmperModule
import org.jetbrains.amper.frontend.Platform
import org.jetbrains.amper.frontend.TaskName
import org.jetbrains.amper.frontend.isDescendantOf
import org.jetbrains.amper.incrementalcache.ExecuteOnChangedInputs
import org.jetbrains.amper.tasks.ResolveExternalDependenciesTask
import org.jetbrains.amper.tasks.SourceRoot
import org.jetbrains.amper.tasks.TaskOutputRoot
import org.jetbrains.amper.tasks.TaskResult
import org.jetbrains.amper.tasks.artifacts.ArtifactTaskBase
import org.jetbrains.amper.tasks.artifacts.KotlinJavaSourceDirArtifact
import org.jetbrains.amper.tasks.artifacts.Selectors
import org.jetbrains.amper.tasks.artifacts.api.Quantifier
import org.jetbrains.amper.tasks.identificationPhrase
import org.jetbrains.amper.util.BuildType
import org.slf4j.LoggerFactory
import java.nio.file.Path
import kotlin.io.path.extension
import kotlin.io.path.pathString
import kotlin.io.path.walk

internal class NativeCompileKlibTask(
    override val module: AmperModule,
    override val platform: Platform,
    private val userCacheRoot: AmperUserCacheRoot,
    private val taskOutputRoot: TaskOutputRoot,
    private val executeOnChangedInputs: ExecuteOnChangedInputs,
    override val taskName: TaskName,
    private val tempRoot: AmperProjectTempRoot,
    override val isTest: Boolean,
    override val buildType: BuildType,
    private val kotlinArtifactsDownloader: KotlinArtifactsDownloader =
        KotlinArtifactsDownloader(userCacheRoot, executeOnChangedInputs),
): ArtifactTaskBase(), BuildTask {
    init {
        require(platform.isLeaf)
        require(platform.isDescendantOf(Platform.NATIVE))
    }

    private val additionalKotlinJavaSourceDirs by Selectors.fromMatchingFragments(
        type = KotlinJavaSourceDirArtifact::class,
        module = module,
        isTest = isTest,
        hasPlatforms = setOf(platform),
        quantifier = Quantifier.AnyOrNone,
    )

    override suspend fun run(dependenciesResult: List<TaskResult>, executionContext: TaskGraphExecutionContext): TaskResult {
        val fragments = module.fragments.filter {
            it.platforms.contains(platform) && it.isTest == isTest
        }
        if (fragments.isEmpty()) {
            error("Zero fragments in module ${module.userReadableName} for platform $platform isTest=$isTest")
        }

        // TODO The native compiler needs recursive dependencies
        val externalDependencies = dependenciesResult
            .filterIsInstance<ResolveExternalDependenciesTask.Result>()
            .flatMap { it.compileClasspath } // compiler dependencies including transitive
            .distinct()
            .filterKLibs()
            .toList()

        logger.debug("" +
                "native compile ${module.userReadableName} -- collected external dependencies" +
                if (externalDependencies.isNotEmpty()) "\n" else "" +
                externalDependencies.sorted().joinToString("\n").prependIndent("  ")
        )

        val compiledModuleDependencies = dependenciesResult
            .filterIsInstance<Result>()
            .mapNotNull { it.compiledKlib }
            .toList()

        // todo native resources are what exactly?

        // TODO kotlin version settings
        val kotlinVersion = UsedVersions.kotlinVersion
        val kotlinUserSettings = fragments.singleLeafFragment().serializableKotlinSettings()

        logger.debug("native compile klib '${module.userReadableName}' -- ${fragments.joinToString(" ") { it.name }}")

        val configuration: Map<String, String> = mapOf(
            "kotlin.version" to kotlinVersion,
            "kotlin.settings" to Json.encodeToString(kotlinUserSettings),
            "task.output.root" to taskOutputRoot.path.pathString,
        )

        val libraryPaths = compiledModuleDependencies + externalDependencies

        val additionalSources = additionalKotlinJavaSourceDirs.map { artifact ->
            SourceRoot(
                fragmentName = artifact.fragmentName,
                path = artifact.path,
            )
        }

        val sources = fragments.map { it.src } + additionalSources.map { it.path }
        val inputs = sources + libraryPaths

        val artifact = executeOnChangedInputs.execute(taskName.name, configuration, inputs) {
            cleanDirectory(taskOutputRoot.path)

            // in Kotlin >= 2.2, we need to list all source files (not just dirs)
            val sourceFiles = sources.flatMap { dir ->
                // konanc only accepts *.kt files, and we need to align with fragment arguments
                dir.walk().filter { it.extension in validSourceFileExtensions }
            }
            if (sourceFiles.isEmpty()) {
                logger.debug("No sources were found for ${fragments.identificationPhrase()}, skipping compilation")
                return@execute ExecuteOnChangedInputs.ExecutionResult(emptyList())
            }

            val artifact = taskOutputRoot.path.resolve(KotlinCompilationType.LIBRARY.outputFilename(module, platform, isTest))

            val nativeCompiler = downloadNativeCompiler(kotlinVersion, userCacheRoot)
            val compilerPlugins = kotlinArtifactsDownloader.downloadCompilerPlugins(
                plugins = kotlinUserSettings.compilerPlugins,
            )
            val args = kotlinNativeCompilerArgs(
                buildType = buildType,
                kotlinUserSettings = kotlinUserSettings,
                compilerPlugins = compilerPlugins,
                entryPoint = null,
                libraryPaths = libraryPaths,
                exportedLibraryPaths = emptyList(),
                fragments = fragments,
                sourceFiles = sourceFiles,
                additionalSourceRoots = additionalSources,
                outputPath = artifact,
                compilationType = KotlinCompilationType.LIBRARY,
                binaryOptions = emptyMap(),
                include = null,
            )

            logger.info("Compiling module '${module.userReadableName}' for platform '${platform.pretty}'...")
            nativeCompiler.compile(args, tempRoot, module)

            return@execute ExecuteOnChangedInputs.ExecutionResult(listOf(artifact))
        }.outputs.singleOrNull()

        return Result(
            compiledKlib = artifact,
            dependencyKlibs = libraryPaths,
            taskName = taskName,
        )
    }

    class Result(
        val compiledKlib: Path?,
        val dependencyKlibs: List<Path>,
        val taskName: TaskName,
    ) : TaskResult

    private val logger = LoggerFactory.getLogger(javaClass)
}
