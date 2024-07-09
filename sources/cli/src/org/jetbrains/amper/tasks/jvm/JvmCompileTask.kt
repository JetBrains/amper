/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.tasks.jvm

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.jetbrains.amper.BuildPrimitives
import org.jetbrains.amper.cli.AmperProjectRoot
import org.jetbrains.amper.cli.Jdk
import org.jetbrains.amper.cli.JdkDownloader
import org.jetbrains.amper.cli.userReadableError
import org.jetbrains.amper.compilation.CombiningKotlinLogger
import org.jetbrains.amper.compilation.CompilationUserSettings
import org.jetbrains.amper.compilation.ErrorsCollectorKotlinLogger
import org.jetbrains.amper.compilation.KotlinArtifactsDownloader
import org.jetbrains.amper.compilation.asKotlinLogger
import org.jetbrains.amper.compilation.downloadCompilerPlugins
import org.jetbrains.amper.compilation.kotlinJvmCompilerArgs
import org.jetbrains.amper.compilation.loadMaybeCachedImpl
import org.jetbrains.amper.compilation.mergedCompilationSettings
import org.jetbrains.amper.compilation.toKotlinProjectId
import org.jetbrains.amper.core.AmperUserCacheRoot
import org.jetbrains.amper.core.UsedVersions
import org.jetbrains.amper.core.extract.cleanDirectory
import org.jetbrains.amper.core.spanBuilder
import org.jetbrains.amper.core.useWithScope
import org.jetbrains.amper.diagnostics.setAmperModule
import org.jetbrains.amper.diagnostics.setListAttribute
import org.jetbrains.amper.frontend.AddToModuleRootsFromCustomTask
import org.jetbrains.amper.frontend.Fragment
import org.jetbrains.amper.frontend.Platform
import org.jetbrains.amper.frontend.PotatoModule
import org.jetbrains.amper.frontend.TaskName
import org.jetbrains.amper.processes.LoggingProcessOutputListener
import org.jetbrains.amper.tasks.BuildTask
import org.jetbrains.amper.tasks.CommonTaskUtils.userReadableList
import org.jetbrains.amper.tasks.ResolveExternalDependenciesTask
import org.jetbrains.amper.tasks.TaskOutputRoot
import org.jetbrains.amper.tasks.TaskResult
import org.jetbrains.amper.tasks.custom.CustomTask
import org.jetbrains.amper.util.ExecuteOnChangedInputs
import org.jetbrains.amper.util.targetLeafPlatforms
import org.jetbrains.kotlin.buildtools.api.CompilationResult
import org.jetbrains.kotlin.buildtools.api.CompilationService
import org.jetbrains.kotlin.buildtools.api.ExperimentalBuildToolsApi
import org.slf4j.LoggerFactory
import java.io.File
import java.nio.file.Path
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
    private val kotlinArtifactsDownloader: KotlinArtifactsDownloader =
        KotlinArtifactsDownloader(userCacheRoot, executeOnChangedInputs),
): BuildTask {

    override val platform: Platform = Platform.JVM

    override suspend fun run(dependenciesResult: List<TaskResult>): TaskResult {
        require(fragments.isNotEmpty()) {
            "fragments list is empty for jvm compile task, module=${module.userReadableName}"
        }

        logger.debug("compile ${module.userReadableName} -- ${fragments.userReadableList()}")

        val mavenDependencies = dependenciesResult
            .filterIsInstance<ResolveExternalDependenciesTask.Result>()
            .singleOrNull()
            ?: error("Expected one and only one dependency on (${ResolveExternalDependenciesTask.Result::class.java.simpleName}) input, but got: ${dependenciesResult.joinToString { it.javaClass.simpleName }}")

        val compileModuleDependencies = dependenciesResult.filterIsInstance<Result>()

        val productionJvmCompileResult = if (isTest) {
            compileModuleDependencies.firstOrNull { it.module == module && !it.isTest }
                ?: error("jvm compilation result from production compilation result was not found for module=${module.userReadableName}, task=$taskName")
        } else null

        val userSettings = fragments.mergedCompilationSettings()

        // TODO Make kotlin version configurable in settings
        val kotlinVersion = UsedVersions.kotlinVersion

        val additionalClasspath = dependenciesResult.filterIsInstance<AdditionalClasspathProviderTaskResult>().flatMap { it.classpath }
        val classpath = compileModuleDependencies.map { it.classesOutputRoot } + mavenDependencies.compileClasspath + additionalClasspath

        val customTasksSources = dependenciesResult.filterIsInstance<CustomTask.Result>()
            .flatMap { result ->
                result.moduleRoots
                    .filter { it.type == AddToModuleRootsFromCustomTask.Type.SOURCES }
                    .map { result.outputDirectory.resolve(it.taskOutputRelativePath) }
            }
        val customTasksResources = dependenciesResult.filterIsInstance<CustomTask.Result>()
            .flatMap { result ->
                result.moduleRoots
                    .filter { it.type == AddToModuleRootsFromCustomTask.Type.RESOURCES }
                    .map { result.outputDirectory.resolve(it.taskOutputRelativePath) }
            }

        // TODO settings
        val jdk = JdkDownloader.getJdk(userCacheRoot)

        val configuration: Map<String, String> = mapOf(
            "jdk.url" to jdk.downloadUrl.toString(),
            "kotlin.version" to kotlinVersion,
            "user.settings" to Json.encodeToString(userSettings),
            "task.output.root" to taskOutputRoot.path.pathString,
            "target.platforms" to module.targetLeafPlatforms.map { it.name }.sorted().joinToString(),
        )

        val sources = fragments.map { it.src.toAbsolutePath() } + customTasksSources
        val resources = fragments.map { it.resourcesPath.toAbsolutePath() } + customTasksResources
        val inputs = sources + resources + classpath

        executeOnChangedInputs.execute(taskName.name, configuration, inputs) {
            cleanDirectory(taskOutputRoot.path)

            val nonEmptySourceDirs = sources
                .filter {
                    when {
                        it.isDirectory() -> it.listDirectoryEntries().isNotEmpty()
                        it.exists() ->
                            error("Source directory at '$it' exists, but it's not a directory, this is currently unsupported")
                        else -> false
                    }
                }

            if (nonEmptySourceDirs.isNotEmpty()) {
                compileSources(
                    jdk = jdk,
                    sourceDirectories = nonEmptySourceDirs,
                    kotlinVersion = kotlinVersion,
                    userSettings = userSettings,
                    classpath = classpath,
                    friendPaths = listOfNotNull(productionJvmCompileResult?.classesOutputRoot),
                )
            } else {
                logger.info("Sources for fragments (${fragments.userReadableList()}) of module '${module.userReadableName}' are missing, skipping compilation")
            }

            val presentResources = resources.filter { it.exists() }
            for (resource in presentResources) {
                logger.info("Copy resources from '$resource' to '${taskOutputRoot.path}'")
                if (resource.isDirectory()) {
                    BuildPrimitives.copy(
                        from = resource,
                        to = taskOutputRoot.path
                    )
                } else {
                    BuildPrimitives.copy(
                        from = resource,
                        to = taskOutputRoot.path.resolve(resource.fileName)
                    )
                }
            }

            return@execute ExecuteOnChangedInputs.ExecutionResult(listOf(taskOutputRoot.path.toAbsolutePath()))
        }

        return Result(
            classesOutputRoot = taskOutputRoot.path.toAbsolutePath(),
            module = module,
            isTest = isTest,
        )
    }

    private suspend fun compileSources(
        jdk: Jdk,
        sourceDirectories: List<Path>,
        kotlinVersion: String,
        userSettings: CompilationUserSettings,
        classpath: List<Path>,
        friendPaths: List<Path>,
    ) {
        for (friendPath in friendPaths) {
            require(classpath.contains(friendPath)) {
                "The classpath must contain all friend paths, but '$friendPath' is not in '${classpath.joinToString(File.pathSeparator)}'"
            }
        }

        val sourcesFiles = sourceDirectories.flatMap { it.walk() }

        val kotlinFilesToCompile = sourcesFiles.filter { it.extension == "kt" }
        if (kotlinFilesToCompile.isNotEmpty()) {
            // Enable multi-platform support only if targeting other than JVM platforms
            // or having a common and JVM fragments (like src and src@jvm directories)
            // TODO This a lot of effort to prevent using -Xmulti-platform in ordinary JVM code
            //  is it worth it? Could we always set -Xmulti-platform?
            val isMultiplatform = (module.targetLeafPlatforms - Platform.JVM).isNotEmpty() || sourceDirectories.size > 1

            compileKotlinSources(
                compilerVersion = kotlinVersion,
                userSettings = userSettings,
                isMultiplatform = isMultiplatform,
                classpath = classpath,
                jdk = jdk,
                sourceDirectories = sourceDirectories,
                friendPaths = friendPaths,
            )
        }

        val javaFilesToCompile = sourcesFiles.filter { it.extension == "java" }
        if (javaFilesToCompile.isNotEmpty()) {
            val kotlinClassesPath = listOf(taskOutputRoot.path)
            val javacSuccess = compileJavaSources(
                jdk = jdk,
                userSettings = userSettings,
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
        userSettings: CompilationUserSettings,
        isMultiplatform: Boolean,
        classpath: List<Path>,
        jdk: Jdk,
        sourceDirectories: List<Path>,
        friendPaths: List<Path>,
    ) {
        // TODO should we download this in a separate task?
        val compilationService = CompilationService.loadMaybeCachedImpl(compilerVersion, kotlinArtifactsDownloader)

        // TODO should we allow users to choose in-process vs daemon?
        // TODO settings for daemon JVM args?
        // FIXME Daemon strategy currently fails with "Can't get connection"
        val executionConfig = compilationService.makeCompilerExecutionStrategyConfiguration()
            .useInProcessStrategy()
            //.useDaemonStrategy(jvmArguments = emptyList())

        // TODO configure incremental compilation here
        val errorsCollector = ErrorsCollectorKotlinLogger()
        val compilationConfig = compilationService.makeJvmCompilationConfiguration()
            .useLogger(CombiningKotlinLogger(logger.asKotlinLogger(), errorsCollector))

        val compilerPlugins = kotlinArtifactsDownloader.downloadCompilerPlugins(compilerVersion, userSettings.kotlin)

        val compilerArgs = kotlinJvmCompilerArgs(
            isMultiplatform = isMultiplatform,
            userSettings = userSettings,
            classpath = classpath,
            jdkHome = jdk.homeDir,
            outputPath = taskOutputRoot.path,
            compilerPlugins = compilerPlugins,
            fragments = fragments,
            friendPaths = friendPaths,
        )

        val kotlinCompilationResult = spanBuilder("kotlin-compilation")
            .setAmperModule(module)
            .setListAttribute("source-dirs", sourceDirectories.map { it.pathString })
            .setListAttribute("fragments", fragments.map { it.name })
            .setListAttribute("compiler-args", compilerArgs)
            .setAttribute("compiler-version", compilerVersion)
            .useWithScope {
                logger.debug("Calling Kotlin compiler...")
                // TODO capture compiler errors/warnings in span (currently stdout/stderr are only logged)
                val projectId = projectRoot.toKotlinProjectId()
                val compilationResult = compilationService.compileJvm(
                    projectId = projectId,
                    strategyConfig = executionConfig,
                    compilationConfig = compilationConfig,
                    sources = sourceDirectories.map { it.toFile() },
                    arguments = compilerArgs,
                )

                logger.debug("Kotlin compiler finalization...")
                compilationService.finishProjectCompilation(projectId)
                compilationResult
            }

        if (kotlinCompilationResult != CompilationResult.COMPILATION_SUCCESS) {
            val errorsSuffix = if (errorsCollector.errors.isNotEmpty()) {
                ":\n\n${errorsCollector.errors.joinToString("\n")}"
            } else " (see errors above)"
            userReadableError("Kotlin compilation failed$errorsSuffix")
        }
    }

    private suspend fun compileJavaSources(
        jdk: Jdk,
        userSettings: CompilationUserSettings,
        classpath: List<Path>,
        javaSourceFiles: List<Path>,
    ): Boolean {
        val javacArgs = buildList {
            if (userSettings.jvmRelease != null) {
                add("--release")
                add(userSettings.jvmRelease.releaseNumber.toString())
            }

            add("-classpath")
            add(classpath.joinToString(File.pathSeparator))

            // necessary for reproducible source jars across OS-es
            add("-encoding")
            add("utf-8")

            // TODO Should we move settings.kotlin.debug to settings.jvm.debug and use it here?
            add("-g")

            // https://blog.ltgt.net/most-build-tools-misuse-javac/
            // we compile module by module, so we don't need javac lookup into other modules
            add("-sourcepath")
            add("")
            add("-implicit:none")

            add("-d")
            add(taskOutputRoot.path.pathString)

            addAll(javaSourceFiles.map { it.pathString })
        }
        val javacCommand = listOf(jdk.javacExecutable.pathString) + javacArgs

        val result = spanBuilder("javac")
            .setAmperModule(module)
            .setListAttribute("args", javacArgs)
            .setAttribute("jdk-home", jdk.homeDir.pathString)
            .setAttribute("version", jdk.version)
            .useWithScope { span ->
                BuildPrimitives.runProcessAndGetOutput(
                    jdk.homeDir,
                    *javacCommand.toTypedArray(),
                    span = span,
                    outputListener = LoggingProcessOutputListener(logger),
                )
            }
        return result.exitCode == 0
    }

    class Result(
        val classesOutputRoot: Path,
        val module: PotatoModule,
        val isTest: Boolean,
    ) : TaskResult

    class AdditionalClasspathProviderTaskResult(
        override val dependencies: List<TaskResult>,
        val classpath: List<Path>
    ) : TaskResult

    private val logger = LoggerFactory.getLogger(javaClass)
}
