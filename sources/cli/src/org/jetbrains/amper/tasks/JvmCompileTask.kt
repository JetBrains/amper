/*
 * Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.tasks

import io.opentelemetry.api.common.AttributeKey
import org.jetbrains.amper.BuildPrimitives
import org.jetbrains.amper.cli.AmperProjectRoot
import org.jetbrains.amper.cli.AmperUserCacheRoot
import org.jetbrains.amper.cli.JdkDownloader
import org.jetbrains.amper.compilation.KotlinCompilerDownloader
import org.jetbrains.amper.compilation.asKotlinLogger
import org.jetbrains.amper.compilation.loadMaybeCachedImpl
import org.jetbrains.amper.compilation.toKotlinProjectId
import org.jetbrains.amper.diagnostics.spanBuilder
import org.jetbrains.amper.diagnostics.useWithScope
import org.jetbrains.amper.downloader.cleanDirectory
import org.jetbrains.amper.engine.TaskName
import org.jetbrains.amper.frontend.Fragment
import org.jetbrains.amper.frontend.KotlinPart
import org.jetbrains.amper.frontend.Platform
import org.jetbrains.amper.frontend.PotatoModule
import org.jetbrains.amper.tasks.CommonTaskUtils.userReadableList
import org.jetbrains.amper.util.ExecuteOnChangedInputs
import org.jetbrains.amper.util.targetLeafPlatforms
import org.jetbrains.kotlin.buildtools.api.CompilationResult
import org.jetbrains.kotlin.buildtools.api.CompilationService
import org.jetbrains.kotlin.buildtools.api.ExperimentalBuildToolsApi
import org.slf4j.LoggerFactory
import java.io.File
import java.nio.file.Path
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.exists
import kotlin.io.path.extension
import kotlin.io.path.isDirectory
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.pathString
import kotlin.io.path.walk

@OptIn(ExperimentalBuildToolsApi::class)
class JvmCompileTask(
    private val module: PotatoModule,
    private val fragments: List<Fragment>,
    private val userCacheRoot: AmperUserCacheRoot,
    private val projectRoot: AmperProjectRoot,
    private val taskOutputRoot: TaskOutputRoot,
    override val taskName: TaskName,
    private val executeOnChangedInputs: ExecuteOnChangedInputs,
    private val kotlinCompilerDownloader: KotlinCompilerDownloader =
        KotlinCompilerDownloader(userCacheRoot, executeOnChangedInputs),
): CompileTask {

    override val platform: Platform = Platform.JVM

    @OptIn(ExperimentalPathApi::class)
    override suspend fun run(dependenciesResult: List<org.jetbrains.amper.tasks.TaskResult>): org.jetbrains.amper.tasks.TaskResult {
        logger.info("compile ${module.userReadableName} -- ${fragments.userReadableList()}")

        val mavenDependencies = dependenciesResult
            .filterIsInstance<ResolveExternalDependenciesTask.TaskResult>()
            .singleOrNull()
            ?: error("Expected one and only one dependency on (${ResolveExternalDependenciesTask.TaskResult::class.java.simpleName}) input, but got: ${dependenciesResult.joinToString { it.javaClass.simpleName }}")

        val immediateDependencies = dependenciesResult.filterIsInstance<TaskResult>()

        // TODO Do we really want different language versions in different fragments?
        val languageVersion = singleLanguageVersion()

        // TODO settings!
        val kotlinVersion = KotlinCompilerDownloader.AMPER_DEFAULT_KOTLIN_VERSION

        val additionalClasspath = dependenciesResult.filterIsInstance<AdditionalClasspathProviderTaskResult>().flatMap { it.classpath }
        val classpath = immediateDependencies.mapNotNull { it.classesOutputRoot } + mavenDependencies.classpath + additionalClasspath

        val configuration: Map<String, String> = mapOf(
            "jdk.url" to JdkDownloader.currentSystemFixedJdkUrl.toString(),
            "kotlin.version" to kotlinVersion,
            "language.version" to (languageVersion ?: ""),
            "task.output.root" to taskOutputRoot.path.pathString,
            "target.platforms" to module.targetLeafPlatforms.map { it.name }.sorted().joinToString(),
        )

        val inputs = fragments.map { it.src } + fragments.map { it.resourcesPath } + classpath

        executeOnChangedInputs.execute(taskName.name, configuration, inputs) {
            cleanDirectory(taskOutputRoot.path)

            val presentSources = fragments
                .map { it.src }
                .filter {
                    when {
                        it.isDirectory() -> it.listDirectoryEntries().isNotEmpty()
                        it.exists() ->
                            error("Source directory at '$it' exists, but it's not a directory, this is currently unsupported")
                        else -> false
                    }
                }

            // Enable multi-platform support only if targeting other than JVM platforms
            // or having a common and JVM fragments (like src and src@jvm directories)
            // TODO This a lot of effort to prevent using -Xmulti-platform in ordinary JVM code
            //  is it worth it? Could we always set -Xmulti-platform?
            val isMultiplatform = (module.targetLeafPlatforms - Platform.JVM).isNotEmpty() || presentSources.size > 1

            if (presentSources.isNotEmpty()) {
                // TODO settings!
                val jdkHome = JdkDownloader.getJdkHome(userCacheRoot)
                val kotlinCompilationResult = compileKotlinSources(
                    compilerVersion = kotlinVersion,
                    languageVersion = languageVersion,
                    isMultiplatform = isMultiplatform,
                    classpath = classpath,
                    jdkHome = jdkHome,
                    sourceFiles = presentSources,
                )
                if (kotlinCompilationResult != CompilationResult.COMPILATION_SUCCESS) {
                    error("Kotlin compilation failed (see errors above)")
                }

                val javaFilesToCompile = presentSources.flatMap { src ->
                    src.walk().filter { it.extension == "java" }
                }
                if (javaFilesToCompile.isNotEmpty()) {
                    val kotlinClassesPath = listOf(taskOutputRoot.path)
                    compileJavaSources(
                        jdkHome = jdkHome,
                        classpath = classpath + kotlinClassesPath,
                        javaSourceFiles = javaFilesToCompile,
                    )
                }
            } else {
                logger.info("Sources for fragments (${fragments.userReadableList()}) of module '${module.userReadableName}' are missing, skipping compilation")
            }

            val presentResources = fragments.map { it.resourcesPath }.filter { it.exists() }
            for (resource in presentResources) {
                logger.info("Copy resources from '$resource' to '${taskOutputRoot.path}'")
                BuildPrimitives.copy(
                    from = resource,
                    to = taskOutputRoot.path
                )
            }

            return@execute ExecuteOnChangedInputs.ExecutionResult(listOf(taskOutputRoot.path))
        }

        return TaskResult(
            classesOutputRoot = taskOutputRoot.path,
            dependencies = dependenciesResult,
        )
    }

    private fun singleLanguageVersion(): String? {
        val kotlinParts = fragments.flatMap { it.parts.filterIsInstance<KotlinPart>() }
        val languageVersions = kotlinParts.mapNotNull { it.languageVersion }.distinct()
        if (languageVersions.size > 1) {
            error("Fragments of module ${module.userReadableName} (${fragments.userReadableList()}) provide several " +
                    "different kotlin language versions: ${languageVersions.sorted().joinToString(" ")}")
        }
        return languageVersions.singleOrNull()
    }

    private suspend fun compileKotlinSources(
        compilerVersion: String,
        languageVersion: String?,
        isMultiplatform: Boolean,
        classpath: List<Path>,
        jdkHome: Path,
        sourceFiles: List<Path>,
    ): CompilationResult {
        // TODO should we download this in a separate task?
        val compilationService = CompilationService.loadMaybeCachedImpl(compilerVersion, kotlinCompilerDownloader)

        // TODO should we allow users to choose in-process vs daemon?
        // TODO settings for daemon JVM args?
        // FIXME Daemon strategy currently fails with "Can't get connection"
        val executionConfig = compilationService.makeCompilerExecutionStrategyConfiguration()
            .useInProcessStrategy()
            //.useDaemonStrategy(jvmArguments = emptyList())

        // TODO configure incremental compilation here
        val compilationConfig = compilationService.makeJvmCompilationConfiguration()
            .useLogger(logger.asKotlinLogger())

        val compilerArgs = buildList {
            if (languageVersion != null) {
                add("-language-version")
                add(languageVersion)
            }

            if (isMultiplatform) {
                add("-Xmulti-platform")
            }

            add("-jvm-target")
            add("17")

            add("-classpath")
            add(classpath.joinToString(File.pathSeparator))

            add("-jdk-home")
            add(jdkHome.pathString)

            add("-no-stdlib")

            add("-d")
            add(taskOutputRoot.path.pathString)
        }

        val kotlinCompilationResult = spanBuilder("kotlin-compilation")
            .setAttribute("amper-module", module.userReadableName)
            .setAttribute(AttributeKey.stringArrayKey("source-files"), sourceFiles.map { it.toString() })
            .setAttribute(AttributeKey.stringArrayKey("compiler-args"), compilerArgs)
            .setAttribute("version", compilerVersion)
            .useWithScope {
                logger.info("Calling Kotlin compiler...")

                // TODO capture compiler errors/warnings in span (currently stdout/stderr are only logged)
                compilationService.compileJvm(
                    projectId = projectRoot.toKotlinProjectId(),
                    strategyConfig = executionConfig,
                    compilationConfig = compilationConfig,
                    sources = sourceFiles.map { it.toFile() },
                    arguments = compilerArgs,
                )
            }
        return kotlinCompilationResult
    }

    private suspend fun compileJavaSources(
        jdkHome: Path,
        classpath: List<Path>,
        javaSourceFiles: List<Path>,
    ) {
        val javacCommand = listOf(
            JdkDownloader.getJavacExecutable(jdkHome).pathString,
            "-classpath", classpath.joinToString(File.pathSeparator),
            // TODO ok by default?
            "-encoding", "utf-8",
            // TODO settings
            "-g",
            // https://blog.ltgt.net/most-build-tools-misuse-javac/
            // we compile module by module, so we don't need javac lookup into other modules
            "-sourcepath", "", "-implicit:none",
            "-d", taskOutputRoot.path.pathString,
        ) + javaSourceFiles.map { it.pathString }

        spanBuilder("javac")
            .setAttribute("amper-module", module.userReadableName)
            .setAttribute(AttributeKey.stringArrayKey("args"), javacCommand)
            .setAttribute("jdk-home", jdkHome.pathString)
            // TODO get version from jdkHome/release
            // .setAttribute("version", jdkHome.)
            .useWithScope { span ->
                BuildPrimitives.runProcessAndAssertExitCode(javacCommand, jdkHome, span)
            }
    }

    class TaskResult(
        override val dependencies: List<org.jetbrains.amper.tasks.TaskResult>,
        val classesOutputRoot: Path?,
    ) : org.jetbrains.amper.tasks.TaskResult

    class AdditionalClasspathProviderTaskResult(
        override val dependencies: List<org.jetbrains.amper.tasks.TaskResult>,
        val classpath: List<Path>
    ) : org.jetbrains.amper.tasks.TaskResult

    private val logger = LoggerFactory.getLogger(javaClass)
}
