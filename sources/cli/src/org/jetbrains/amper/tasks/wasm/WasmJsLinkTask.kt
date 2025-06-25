/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.tasks.wasm

import kotlinx.serialization.json.Json
import org.jetbrains.amper.cli.AmperProjectTempRoot
import org.jetbrains.amper.cli.telemetry.setAmperModule
import org.jetbrains.amper.cli.userReadableError
import org.jetbrains.amper.compilation.KotlinArtifactsDownloader
import org.jetbrains.amper.compilation.KotlinCompilationType
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
import org.jetbrains.amper.tasks.TaskOutputRoot
import org.jetbrains.amper.tasks.TaskResult
import org.jetbrains.amper.tasks.native.filterKLibs
import org.jetbrains.amper.telemetry.setListAttribute
import org.jetbrains.amper.telemetry.use
import org.slf4j.LoggerFactory
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.pathString

class WasmJsLinkTask(
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
    private val kotlinArtifactsDownloader: KotlinArtifactsDownloader =
        KotlinArtifactsDownloader(userCacheRoot, executeOnChangedInputs),
) : BuildTask {
    init {
        require(platform.isLeaf)
        require(platform.isDescendantOf(Platform.WASM))
        require(compilationType == KotlinCompilationType.BINARY)
    }

    override suspend fun run(
        dependenciesResult: List<TaskResult>,
        executionContext: TaskGraphExecutionContext
    ): Result {
        val fragments = module.fragments.filter {
            it.platforms.contains(platform) && it.isTest == isTest
        }
        if (fragments.isEmpty()) {
            error("Zero fragments in module ${module.userReadableName} for platform $platform isTest=$isTest")
        }

        val externalKLibs = dependenciesResult
            .filterIsInstance<ResolveExternalDependenciesTask.Result>()
            .flatMap { it.runtimeClasspath } // runtime dependencies including transitive
            .distinct()
            .filterKLibs()
            .toList()

        val includeArtifact = dependenciesResult
            .filterIsInstance<WasmJsCompileKlibTask.Result>()
            .firstOrNull { it.taskName == compileKLibTaskName }
            ?.compiledKlib
            ?: error("The result of the klib compilation task (${compileKLibTaskName.name}) was not found")

        val compileKLibDependencies = dependenciesResult
            .filterIsInstance<WasmJsCompileKlibTask.Result>()
            .filter { it.taskName != compileKLibTaskName }

        val compileKLibs = compileKLibDependencies.map { it.compiledKlib }

        // TODO kotlin version settings
        val kotlinVersion = UsedVersions.kotlinVersion
        val kotlinUserSettings = fragments.mergedKotlinSettings()

        logger.debug("WasmJS link '${module.userReadableName}' -- ${fragments.joinToString(" ") { it.name }}")

        val configuration: Map<String, String> = mapOf(
            "kotlin.version" to kotlinVersion,
            "kotlin.settings" to Json.encodeToString(kotlinUserSettings),
            "task.output.root" to taskOutputRoot.path.pathString,
        )

        val inputs = listOf(includeArtifact) + compileKLibs

        val jdk = JdkDownloader.getJdk(userCacheRoot)

        val artifact = executeOnChangedInputs.execute(taskName.name, configuration, inputs) {
            cleanDirectory(taskOutputRoot.path)

            val artifactPath = taskOutputRoot.path

            compileSources(
                jdk,
                kotlinVersion = kotlinVersion,
                kotlinUserSettings = kotlinUserSettings,
                librariesPaths = compileKLibs + externalKLibs + listOf(includeArtifact),
                includeArtifact = includeArtifact,
            )

            return@execute ExecuteOnChangedInputs.ExecutionResult(listOf(artifactPath))
        }.outputs.single()

        return Result(
            linkedBinary = artifact,
        )
    }

    private suspend fun compileSources(
        jdk: Jdk,
        kotlinVersion: String,
        kotlinUserSettings: KotlinUserSettings,
        librariesPaths: List<Path>,
        includeArtifact: Path?,
    ) {
        val compilerJars = kotlinArtifactsDownloader.downloadKotlinCompilerEmbeddable(version = kotlinVersion)
        val compilerPlugins = kotlinArtifactsDownloader.downloadCompilerPlugins(kotlinVersion, kotlinUserSettings)
        val compilerArgs = kotlinWasmJsCompilerArgs(
            kotlinUserSettings = kotlinUserSettings,
            compilerPlugins = compilerPlugins,
            libraryPaths = librariesPaths,
            outputPath = taskOutputRoot.path,
            friendPaths = emptyList(),
            fragments = emptyList(),
            sourceFiles = emptyList(),
            additionalSourceRoots = emptyList(),
            moduleName = module.kotlinModuleName(isTest),
            compilationType = compilationType,
            include = includeArtifact,
        )

        if (isTest) {
            logger.debug("Linking wasm js test executable for module '${module.userReadableName}' on platform '${platform.pretty}'...")
        } else {
            logger.info("Linking wasm js '${platform.pretty}' executable for module '${module.userReadableName}'...")
        }
        spanBuilder("kotlin-wasm-js-link")
            .setAmperModule(module)
            .setAttribute("compiler-version", kotlinVersion)
            .setListAttribute("compiler-args", compilerArgs)
            .use {
                logger.info("Linking Kotlin Wasm JS for module '${module.userReadableName}'...")
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
                    userReadableError("Kotlin WasmJS linking failed (see errors above)")
                }
            }
    }

    class Result(
        val linkedBinary: Path,
    ) : TaskResult

    private val logger = LoggerFactory.getLogger(javaClass)
}
