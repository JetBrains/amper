/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.tasks.wasm

import kotlinx.serialization.json.Json
import org.jetbrains.amper.cli.AmperProjectTempRoot
import org.jetbrains.amper.cli.telemetry.setAmperModule
import org.jetbrains.amper.cli.userReadableError
import org.jetbrains.amper.compilation.KotlinArtifactsDownloader
import org.jetbrains.amper.compilation.KotlinUserSettings
import org.jetbrains.amper.compilation.downloadCompilerPlugins
import org.jetbrains.amper.compilation.kotlinModuleName
import org.jetbrains.amper.compilation.kotlinWasmJsCompilerArgs
import org.jetbrains.amper.compilation.mergedKotlinSettings
import org.jetbrains.amper.core.AmperUserCacheRoot
import org.jetbrains.amper.core.UsedVersions
import org.jetbrains.amper.core.extract.cleanDirectory
import org.jetbrains.amper.core.telemetry.spanBuilder
import org.jetbrains.amper.engine.BuildTask
import org.jetbrains.amper.engine.TaskGraphExecutionContext
import org.jetbrains.amper.frontend.AmperModule
import org.jetbrains.amper.frontend.Platform
import org.jetbrains.amper.frontend.TaskName
import org.jetbrains.amper.frontend.isDescendantOf
import org.jetbrains.amper.incrementalcache.ExecuteOnChangedInputs
import org.jetbrains.amper.jvm.Jdk
import org.jetbrains.amper.jvm.JdkDownloader
import org.jetbrains.amper.processes.LoggingProcessOutputListener
import org.jetbrains.amper.processes.runJava
import org.jetbrains.amper.tasks.ResolveExternalDependenciesTask
import org.jetbrains.amper.tasks.SourceRoot
import org.jetbrains.amper.tasks.TaskOutputRoot
import org.jetbrains.amper.tasks.TaskResult
import org.jetbrains.amper.tasks.artifacts.ArtifactTaskBase
import org.jetbrains.amper.tasks.artifacts.KotlinJavaSourceDirArtifact
import org.jetbrains.amper.tasks.artifacts.Selectors
import org.jetbrains.amper.tasks.artifacts.api.Quantifier
import org.jetbrains.amper.tasks.native.filterKLibs
import org.jetbrains.amper.telemetry.setListAttribute
import org.jetbrains.amper.telemetry.use
import org.slf4j.LoggerFactory
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.createTempFile
import kotlin.io.path.deleteExisting
import kotlin.io.path.exists
import kotlin.io.path.pathString

class WasmJsCompileKlibTask(
    override val module: AmperModule,
    override val platform: Platform,
    private val userCacheRoot: AmperUserCacheRoot,
    private val taskOutputRoot: TaskOutputRoot,
    private val executeOnChangedInputs: ExecuteOnChangedInputs,
    override val taskName: TaskName,
    private val tempRoot: AmperProjectTempRoot,
    override val isTest: Boolean,
    private val kotlinArtifactsDownloader: KotlinArtifactsDownloader =
        KotlinArtifactsDownloader(userCacheRoot, executeOnChangedInputs),
) : ArtifactTaskBase(), BuildTask {
    init {
        require(platform.isLeaf)
        require(platform.isDescendantOf(Platform.WASM))
    }

    private val additionalKotlinJavaSourceDirs by Selectors.fromMatchingFragments(
        type = KotlinJavaSourceDirArtifact::class,
        module = module,
        isTest = isTest,
        hasPlatforms = setOf(platform),
        quantifier = Quantifier.AnyOrNone,
    )

    override suspend fun run(
        dependenciesResult: List<TaskResult>,
        executionContext: TaskGraphExecutionContext
    ): TaskResult {
        val fragments = module.fragments.filter {
            it.platforms.contains(platform) && it.isTest == isTest
        }
        if (fragments.isEmpty()) {
            error("Zero fragments in module ${module.userReadableName} for platform $platform isTest=$isTest")
        }

        // TODO The IR compiler needs recursive dependencies
        val externalDependencies = dependenciesResult
            .filterIsInstance<ResolveExternalDependenciesTask.Result>()
            .flatMap { it.compileClasspath }
            .distinct()
            .filterKLibs()
            .toList()

        logger.debug(
            "" +
                    "Wasm compile ${module.userReadableName} -- collected external dependencies" +
                    if (externalDependencies.isNotEmpty()) "\n" else "" +
                            externalDependencies.sorted().joinToString("\n").prependIndent("  ")
        )

        val compileModuleDependencies = dependenciesResult.filterIsInstance<Result>()

        val compiledKlibModuleDependencies = compileModuleDependencies
            .map { it.compiledKlib }
            .toList()

        // TODO kotlin version settings
        val kotlinVersion = UsedVersions.kotlinVersion
        val kotlinUserSettings = fragments.mergedKotlinSettings()

        logger.debug("wasm compile klib '${module.userReadableName}' -- ${fragments.joinToString(" ") { it.name }}")

        val jdk = JdkDownloader.getJdk(userCacheRoot)

        val configuration: Map<String, String> = mapOf(
            "kotlin.version" to kotlinVersion,
            "kotlin.settings" to Json.encodeToString(kotlinUserSettings),
            "task.output.root" to taskOutputRoot.path.pathString,
        )

        val libraryPaths = compiledKlibModuleDependencies + externalDependencies

        val additionalSources = additionalKotlinJavaSourceDirs.map { artifact ->
            SourceRoot(
                fragmentName = artifact.fragmentName,
                path = artifact.path,
            )
        }

        val productionWasmJsCompileResult = if (isTest) {
            compileModuleDependencies.firstOrNull { it.module == module && !it.isTest }
                ?: error("jvm compilation result from production compilation result was not found for module=${module.userReadableName}, task=$taskName")
        } else null

        val sources = fragments.map { it.src } + additionalSources.map { it.path }
        val inputs = sources + libraryPaths

        val artifact = executeOnChangedInputs.execute(taskName.name, configuration, inputs) {
            cleanDirectory(taskOutputRoot.path)

            val artifact = taskOutputRoot.path

            val tempFilesToDelete = mutableListOf<Path>()

            try {
                val existingSourceRoots = sources.filter { it.exists() }
                val rootsToCompile = existingSourceRoots.ifEmpty {
                    // konanc does not want to compile an application with zero sources files,
                    // but it's a perfectly valid situation where all code is in shared libraries
                    val emptyKotlinFile = createTempFile(tempRoot.path, "empty", ".kt")
                        .also { tempFilesToDelete.add(it) }
                    listOf(emptyKotlinFile)
                }

                compileSources(
                    jdk = jdk,
                    kotlinVersion = kotlinVersion,
                    kotlinUserSettings = kotlinUserSettings,
                    sourceDirectories = rootsToCompile,
                    additionalSourceRoots = additionalSources,
                    librariesPaths = libraryPaths,
                    friendPaths = listOfNotNull(productionWasmJsCompileResult?.compiledKlib),
                )

                logger.info("Compiling module '${module.userReadableName}' for platform '${platform.pretty}'...")
            } finally {
                for (tempPath in tempFilesToDelete) {
                    tempPath.deleteExisting()
                }
            }

            return@execute ExecuteOnChangedInputs.ExecutionResult(listOf(artifact))
        }.outputs.single()

        return Result(
            compiledKlib = artifact,
            module = module,
            isTest = isTest,
            taskName = taskName,
        )
    }

    private suspend fun compileSources(
        jdk: Jdk,
        kotlinVersion: String,
        kotlinUserSettings: KotlinUserSettings,
        sourceDirectories: List<Path>,
        additionalSourceRoots: List<SourceRoot>,
        librariesPaths: List<Path>,
        friendPaths: List<Path>,
    ) {
        val compilerJars = kotlinArtifactsDownloader.downloadKotlinCompilerEmbeddable(version = kotlinVersion)
        val compilerPlugins = kotlinArtifactsDownloader.downloadCompilerPlugins(
            kotlinVersion = kotlinVersion,
            kotlinUserSettings = kotlinUserSettings,
        )

        val fragments = module.fragments.filter {
            it.platforms.contains(platform) && it.isTest == isTest
        }

        val compilerArgs = kotlinWasmJsCompilerArgs(
            kotlinUserSettings = kotlinUserSettings,
            compilerPlugins = compilerPlugins,
            libraryPaths = librariesPaths,
            outputPath = taskOutputRoot.path,
            friendPaths = friendPaths,
            fragments = fragments,
            sourceFiles = sourceDirectories,
            additionalSourceRoots = additionalSourceRoots,
            moduleName = module.kotlinModuleName(isTest)
        )
        spanBuilder("kotlin-wasm-js-compilation")
            .setAmperModule(module)
            .setListAttribute("source-dirs", sourceDirectories.map { it.pathString })
            .setAttribute("compiler-version", kotlinVersion)
            .setListAttribute("compiler-args", compilerArgs)
            .use {
                logger.info("Compiling Kotlin Wasm JS for module '${module.userReadableName}'...")
                val result = jdk.runJava(
                    workingDir = Path("."),
                    mainClass = "org.jetbrains.kotlin.cli.js.K2JSCompiler",
                    classpath = compilerJars,
                    programArgs = compilerArgs,
                    jvmArgs = listOf(),
                    outputListener = LoggingProcessOutputListener(logger),
                    tempRoot = tempRoot,
                )
                if (result.exitCode != 0) {
                    userReadableError("Kotlin metadata compilation failed (see errors above)")
                }
            }
    }

    class Result(
        val compiledKlib: Path,
        val module: AmperModule,
        val isTest: Boolean,
        val taskName: TaskName,
    ) : TaskResult

    private val logger = LoggerFactory.getLogger(javaClass)
}
