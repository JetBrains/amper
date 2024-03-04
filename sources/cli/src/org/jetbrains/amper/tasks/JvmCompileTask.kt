/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.tasks

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.jetbrains.amper.BuildPrimitives
import org.jetbrains.amper.cli.AmperProjectRoot
import org.jetbrains.amper.cli.AmperUserCacheRoot
import org.jetbrains.amper.cli.JdkDownloader
import org.jetbrains.amper.cli.userReadableError
import org.jetbrains.amper.compilation.CompilerPlugin
import org.jetbrains.amper.compilation.KotlinCompilerDownloader
import org.jetbrains.amper.compilation.KotlinUserSettings
import org.jetbrains.amper.compilation.asKotlinLogger
import org.jetbrains.amper.compilation.kotlinJvmCompilerArgs
import org.jetbrains.amper.compilation.loadMaybeCachedImpl
import org.jetbrains.amper.compilation.mergedKotlinSettings
import org.jetbrains.amper.compilation.toKotlinProjectId
import org.jetbrains.amper.core.UsedVersions
import org.jetbrains.amper.core.extract.cleanDirectory
import org.jetbrains.amper.diagnostics.setAmperModule
import org.jetbrains.amper.diagnostics.setListAttribute
import org.jetbrains.amper.diagnostics.spanBuilder
import org.jetbrains.amper.diagnostics.useWithScope
import org.jetbrains.amper.engine.TaskName
import org.jetbrains.amper.frontend.Fragment
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
    override val module: PotatoModule,
    override val isTest: Boolean,
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

    override suspend fun run(dependenciesResult: List<org.jetbrains.amper.tasks.TaskResult>): org.jetbrains.amper.tasks.TaskResult {
        require(fragments.isNotEmpty()) {
            "fragments list is empty for jvm compile task, module=${module.userReadableName}"
        }

        logger.info("compile ${module.userReadableName} -- ${fragments.userReadableList()}")

        val mavenDependencies = dependenciesResult
            .filterIsInstance<ResolveExternalDependenciesTask.TaskResult>()
            .singleOrNull()
            ?: error("Expected one and only one dependency on (${ResolveExternalDependenciesTask.TaskResult::class.java.simpleName}) input, but got: ${dependenciesResult.joinToString { it.javaClass.simpleName }}")

        val immediateDependencies = dependenciesResult.filterIsInstance<TaskResult>()

        val productionJvmCompileResult = if (isTest) {
            immediateDependencies.firstOrNull { it.module == module && !it.isTest }
                ?: error("jvm compilation result from production compilation result was not found for module=${module.userReadableName}, task=$taskName")
        } else null

        val kotlinUserSettings = fragments.mergedKotlinSettings()

        // TODO Make kotlin version configurable in settings
        val kotlinVersion = UsedVersions.kotlinVersion

        val additionalClasspath = dependenciesResult.filterIsInstance<AdditionalClasspathProviderTaskResult>().flatMap { it.classpath }
        val classpath = immediateDependencies.mapNotNull { it.classesOutputRoot } + mavenDependencies.compileClasspath + additionalClasspath

        val configuration: Map<String, String> = mapOf(
            "jdk.url" to JdkDownloader.currentSystemFixedJdkUrl.toString(),
            "kotlin.version" to kotlinVersion,
            "kotlin.settings" to Json.encodeToString(kotlinUserSettings),
            "task.output.root" to taskOutputRoot.path.pathString,
            "target.platforms" to module.targetLeafPlatforms.map { it.name }.sorted().joinToString(),
        )

        val sources = fragments.map { it.src.toAbsolutePath() }
        val resources = fragments.map { it.resourcesPath.toAbsolutePath() }
        val inputs = sources + resources + classpath

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

            if (presentSources.isNotEmpty()) {
                compileSources(
                    sourceDirectories = presentSources,
                    kotlinVersion = kotlinVersion,
                    kotlinUserSettings = kotlinUserSettings,
                    classpath = classpath,
                    friendlyClasspath = listOfNotNull(productionJvmCompileResult?.classesOutputRoot),
                )
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

            return@execute ExecuteOnChangedInputs.ExecutionResult(listOf(taskOutputRoot.path.toAbsolutePath()))
        }

        return TaskResult(
            classesOutputRoot = taskOutputRoot.path.toAbsolutePath(),
            dependencies = dependenciesResult,
            module = module,
            isTest = isTest,
        )
    }

    @OptIn(ExperimentalPathApi::class)
    private suspend fun compileSources(
        sourceDirectories: List<Path>,
        kotlinVersion: String,
        kotlinUserSettings: KotlinUserSettings,
        classpath: List<Path>,
        friendlyClasspath: List<Path>,
    ) {
        for (friendlyClasspathElement in friendlyClasspath) {
            require(classpath.contains(friendlyClasspathElement)) {
                "Classpath must contain all friendly classpath element. " +
                        "'$friendlyClasspathElement' is not in '${classpath.joinToString(File.pathSeparator)}'"
            }
        }

        val jdkHome = JdkDownloader.getJdkHome(userCacheRoot)

        val sourcesFiles = sourceDirectories.flatMap { it.walk() }

        val kotlinFilesToCompile = sourcesFiles.filter { it.extension == "kt" }
        if (kotlinFilesToCompile.isNotEmpty()) {
            // Enable multi-platform support only if targeting other than JVM platforms
            // or having a common and JVM fragments (like src and src@jvm directories)
            // TODO This a lot of effort to prevent using -Xmulti-platform in ordinary JVM code
            //  is it worth it? Could we always set -Xmulti-platform?
            val isMultiplatform = (module.targetLeafPlatforms - Platform.JVM).isNotEmpty() || sourceDirectories.size > 1

            // TODO settings!
            val kotlinCompilationResult = compileKotlinSources(
                compilerVersion = kotlinVersion,
                kotlinUserSettings = kotlinUserSettings,
                isMultiplatform = isMultiplatform,
                classpath = classpath,
                jdkHome = jdkHome,
                sourceFiles = sourceDirectories,
                friendlyClasspath = friendlyClasspath,
            )
            if (kotlinCompilationResult != CompilationResult.COMPILATION_SUCCESS) {
                userReadableError("Kotlin compilation failed (see errors above)")
            }
        }

        val javaFilesToCompile = sourcesFiles.filter { it.extension == "java" }
        if (javaFilesToCompile.isNotEmpty()) {
            val kotlinClassesPath = listOf(taskOutputRoot.path)
            val javacSuccess = compileJavaSources(
                jdkHome = jdkHome,
                classpath = classpath + kotlinClassesPath,
                javaSourceFiles = javaFilesToCompile,
            )
            if (!javacSuccess) {
                userReadableError("Java compilation failed (see errors above)")
            }
        }
    }

    private suspend fun compileKotlinSources(
        compilerVersion: String,
        kotlinUserSettings: KotlinUserSettings,
        isMultiplatform: Boolean,
        classpath: List<Path>,
        jdkHome: Path,
        sourceFiles: List<Path>,
        friendlyClasspath: List<Path>,
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

        val compilerPlugins = buildList {
            if (kotlinUserSettings.serializationEnabled) {
                val jarPath = kotlinCompilerDownloader.downloadKotlinSerializationPlugin(compilerVersion)
                add(CompilerPlugin.serialization(jarPath))
            }
            if (kotlinUserSettings.composeEnabled) {
                val jarPath = kotlinCompilerDownloader.downloadKotlinComposePlugin(compilerVersion)
                add(CompilerPlugin.compose(jarPath))
            }
        }

        val compilerArgs = kotlinJvmCompilerArgs(
            isMultiplatform = isMultiplatform,
            kotlinUserSettings = kotlinUserSettings,
            classpath = classpath,
            jdkHome = jdkHome,
            outputPath = taskOutputRoot.path,
            compilerPlugins = compilerPlugins,
            friendPaths = friendlyClasspath,
        )

        val kotlinCompilationResult = spanBuilder("kotlin-compilation")
            .setAmperModule(module)
            .setListAttribute("source-files", sourceFiles.map { it.toString() })
            .setListAttribute("compiler-args", compilerArgs)
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
    ): Boolean {
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

        val result = spanBuilder("javac")
            .setAmperModule(module)
            .setListAttribute("args", javacCommand)
            .setAttribute("jdk-home", jdkHome.pathString)
            // TODO get version from jdkHome/release
            // .setAttribute("version", jdkHome.)
            .useWithScope { span ->
                BuildPrimitives.runProcessAndGetOutput(javacCommand, jdkHome, span)
            }
        return result.exitCode == 0
    }

    class TaskResult(
        override val dependencies: List<org.jetbrains.amper.tasks.TaskResult>,
        val classesOutputRoot: Path?,
        val module: PotatoModule,
        val isTest: Boolean,
    ) : org.jetbrains.amper.tasks.TaskResult

    class AdditionalClasspathProviderTaskResult(
        override val dependencies: List<org.jetbrains.amper.tasks.TaskResult>,
        val classpath: List<Path>
    ) : org.jetbrains.amper.tasks.TaskResult

    private val logger = LoggerFactory.getLogger(javaClass)
}
