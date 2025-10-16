/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.tasks.web

import kotlinx.serialization.json.Json
import org.jetbrains.amper.cli.AmperProjectTempRoot
import org.jetbrains.amper.cli.telemetry.setAmperModule
import org.jetbrains.amper.cli.userReadableError
import org.jetbrains.amper.compilation.KotlinArtifactsDownloader
import org.jetbrains.amper.compilation.KotlinCompilationType
import org.jetbrains.amper.compilation.KotlinUserSettings
import org.jetbrains.amper.compilation.ResolvedCompilerPlugin
import org.jetbrains.amper.compilation.downloadCompilerPlugins
import org.jetbrains.amper.compilation.kotlinModuleName
import org.jetbrains.amper.compilation.serializableKotlinSettings
import org.jetbrains.amper.compilation.singleLeafFragment
import org.jetbrains.amper.core.AmperUserCacheRoot
import org.jetbrains.amper.core.extract.cleanDirectory
import org.jetbrains.amper.core.telemetry.spanBuilder
import org.jetbrains.amper.engine.BuildTask
import org.jetbrains.amper.engine.TaskGraphExecutionContext
import org.jetbrains.amper.frontend.AmperModule
import org.jetbrains.amper.frontend.Fragment
import org.jetbrains.amper.frontend.Platform
import org.jetbrains.amper.frontend.TaskName
import org.jetbrains.amper.frontend.isDescendantOf
import org.jetbrains.amper.frontend.jdkSettings
import org.jetbrains.amper.incrementalcache.IncrementalCache
import org.jetbrains.amper.incrementalcache.executeForFiles
import org.jetbrains.amper.jdk.provisioning.Jdk
import org.jetbrains.amper.jdk.provisioning.JdkProvider
import org.jetbrains.amper.jvm.getJdkOrUserError
import org.jetbrains.amper.processes.ArgsMode
import org.jetbrains.amper.processes.LoggingProcessOutputListener
import org.jetbrains.amper.processes.runJava
import org.jetbrains.amper.tasks.ResolveExternalDependenciesTask
import org.jetbrains.amper.tasks.SourceRoot
import org.jetbrains.amper.tasks.TaskOutputRoot
import org.jetbrains.amper.tasks.TaskResult
import org.jetbrains.amper.tasks.identificationPhrase
import org.jetbrains.amper.tasks.native.filterKLibs
import org.jetbrains.amper.telemetry.setListAttribute
import org.jetbrains.amper.telemetry.use
import org.jetbrains.amper.util.BuildType
import org.slf4j.LoggerFactory
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.pathString

internal abstract class WebLinkTask(
    override val module: AmperModule,
    override val platform: Platform,
    private val userCacheRoot: AmperUserCacheRoot,
    private val jdkProvider: JdkProvider,
    private val taskOutputRoot: TaskOutputRoot,
    private val incrementalCache: IncrementalCache,
    override val taskName: TaskName,
    private val tempRoot: AmperProjectTempRoot,
    override val isTest: Boolean,
    override val buildType: BuildType? = null,
    /**
     * The name of the task that produces the klib for the sources of this module.
     */
    val compileKLibTaskName: TaskName,
    /**
     * Task names that produce klibs that need to be exposed as API in the resulting artifact.
     */
    private val kotlinArtifactsDownloader: KotlinArtifactsDownloader =
        KotlinArtifactsDownloader(userCacheRoot, incrementalCache),
) : BuildTask {

    abstract val expectedPlatform: Platform

    init {
        require(platform.isLeaf)
        require(platform.isDescendantOf(expectedPlatform))
    }

    override suspend fun run(
        dependenciesResult: List<TaskResult>,
        executionContext: TaskGraphExecutionContext,
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
            .filterIsInstance<WebCompileKlibTask.Result>()
            .firstOrNull { it.taskName == compileKLibTaskName }
            ?.compiledKlib

        if (includeArtifact == null && isTest) {
            // We may skip linking for test specifically if there's no compiled code in the fragments.
            // Libraries are of no interest here because they can't contain any tests
            logger.info("No test code was found compiled for ${fragments.identificationPhrase()}, skipping linking")
            return Result(
                linkedBinary = null,
            )
        }

        val compileKLibDependencies = dependenciesResult
            .filterIsInstance<WebCompileKlibTask.Result>()
            .filter { it.taskName != compileKLibTaskName }

        val compiledKLibs = compileKLibDependencies.mapNotNull { it.compiledKlib }

        val kotlinUserSettings = fragments.singleLeafFragment().serializableKotlinSettings()
        val jdk = jdkProvider.getJdkOrUserError(module.jdkSettings)

        logger.debug("${expectedPlatform.name} link '${module.userReadableName}' -- ${fragments.joinToString(" ") { it.name }}")

        val inputs = compiledKLibs + listOfNotNull(includeArtifact)


        val artifact = incrementalCache.executeForFiles(
            key = taskName.name,
            inputValues = mapOf(
                "kotlin.settings" to Json.encodeToString<KotlinUserSettings>(kotlinUserSettings),
                "task.output.root" to taskOutputRoot.path.pathString,
            ),
            inputFiles = inputs,
        ) {
            cleanDirectory(taskOutputRoot.path)

            val artifactPath = taskOutputRoot.path

            compileSources(
                jdk,
                kotlinUserSettings = kotlinUserSettings,
                librariesPaths = externalKLibs + inputs,
                includeArtifact = includeArtifact,
            )

            listOf(artifactPath)
        }.single()

        return Result(
            linkedBinary = artifact,
        )
    }

    private suspend fun compileSources(
        jdk: Jdk,
        kotlinUserSettings: KotlinUserSettings,
        librariesPaths: List<Path>,
        includeArtifact: Path?,
    ) {
        val compilerJars = kotlinArtifactsDownloader.downloadKotlinCompilerEmbeddable(
            version = kotlinUserSettings.compilerVersion,
        )
        val compilerPlugins = kotlinArtifactsDownloader.downloadCompilerPlugins(
            plugins = kotlinUserSettings.compilerPlugins,
        )
        val compilerArgs = kotlinCompilerArgs(
            kotlinUserSettings = kotlinUserSettings,
            compilerPlugins = compilerPlugins,
            libraryPaths = librariesPaths,
            outputPath = taskOutputRoot.path,
            friendPaths = emptyList(),
            fragments = emptyList(),
            sourceFiles = emptyList(),
            additionalSourceRoots = emptyList(),
            moduleName = module.kotlinModuleName(isTest),
            compilationType = KotlinCompilationType.BINARY,
            include = includeArtifact,
        )

        if (isTest) {
            logger.debug("Linking ${expectedPlatform.name} test executable for module '${module.userReadableName}' on platform '${platform.pretty}'...")
        } else {
            logger.info("Linking ${expectedPlatform.name} '${platform.pretty}' executable for module '${module.userReadableName}'...")
        }
        spanBuilder("kotlin-${expectedPlatform.name.lowercase()}-link")
            .setAmperModule(module)
            .setAttribute("compiler-version", kotlinUserSettings.compilerVersion)
            .setListAttribute("compiler-args", compilerArgs)
            .use {
                logger.info("Linking Kotlin ${expectedPlatform.name} for module '${module.userReadableName}'...")
                val result = jdk.runJava(
                    workingDir = Path("."),
                    mainClass = "org.jetbrains.kotlin.cli.js.K2JSCompiler",
                    classpath = compilerJars,
                    programArgs = compilerArgs,
                    jvmArgs = listOf(),
                    argsMode = ArgsMode.ArgFile(tempRoot = tempRoot),
                    outputListener = LoggingProcessOutputListener(logger),
                )
                if (result.exitCode != 0) {
                    userReadableError("Kotlin ${expectedPlatform.name} linking failed (see errors above)")
                }
            }
    }

    internal abstract fun kotlinCompilerArgs(
        kotlinUserSettings: KotlinUserSettings,
        compilerPlugins: List<ResolvedCompilerPlugin>,
        libraryPaths: List<Path>,
        outputPath: Path,
        friendPaths: List<Path>,
        fragments: List<Fragment>,
        sourceFiles: List<Path>,
        additionalSourceRoots: List<SourceRoot>,
        moduleName: String,
        compilationType: KotlinCompilationType,
        include: Path?,
    ): List<String>

    class Result(
        /**
         * Resulting file path produced by the link stage for JS or Wasm compilation.
         *
         * Null indicates a test compilation where no main module KLIB was produced
         * (i.e., linking is skipped because there is no compiled test code to link).
         */
        val linkedBinary: Path?,
    ) : TaskResult

    private val logger = LoggerFactory.getLogger(javaClass)
}
