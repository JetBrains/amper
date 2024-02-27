/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.tasks

import org.jetbrains.amper.BuildPrimitives
import org.jetbrains.amper.cli.AmperProjectRoot
import org.jetbrains.amper.cli.AmperUserCacheRoot
import org.jetbrains.amper.cli.JdkDownloader
import org.jetbrains.amper.cli.userReadableError
import org.jetbrains.amper.compilation.KotlinCompilerDownloader
import org.jetbrains.amper.compilation.KotlinUserSettings
import org.jetbrains.amper.compilation.asKotlinLogger
import org.jetbrains.amper.compilation.kotlinCompilerArgs
import org.jetbrains.amper.compilation.loadMaybeCachedImpl
import org.jetbrains.amper.compilation.toKotlinProjectId
import org.jetbrains.amper.core.extract.cleanDirectory
import org.jetbrains.amper.diagnostics.setAmperModule
import org.jetbrains.amper.diagnostics.setListAttribute
import org.jetbrains.amper.diagnostics.spanBuilder
import org.jetbrains.amper.diagnostics.useWithScope
import org.jetbrains.amper.engine.TaskName
import org.jetbrains.amper.frontend.Fragment
import org.jetbrains.amper.frontend.Platform
import org.jetbrains.amper.frontend.PotatoModule
import org.jetbrains.amper.frontend.schema.KotlinSettings
import org.jetbrains.amper.frontend.schema.Settings
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
    private val isTest: Boolean,
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
        val kotlinVersion = KotlinCompilerDownloader.AMPER_DEFAULT_KOTLIN_VERSION

        val additionalClasspath = dependenciesResult.filterIsInstance<AdditionalClasspathProviderTaskResult>().flatMap { it.classpath }
        val classpath = immediateDependencies.mapNotNull { it.classesOutputRoot } + mavenDependencies.compileClasspath + additionalClasspath

        val configuration: Map<String, String> = mapOf(
            "jdk.url" to JdkDownloader.currentSystemFixedJdkUrl.toString(),
            "kotlin.version" to kotlinVersion,
            "kotlin.settings" to kotlinUserSettings.toString(),
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

            return@execute ExecuteOnChangedInputs.ExecutionResult(listOf(taskOutputRoot.path))
        }

        return TaskResult(
            classesOutputRoot = taskOutputRoot.path,
            dependencies = dependenciesResult,
            module = module,
            isTest = isTest,
        )
    }

    // TODO Consider for which Kotlin settings we should enforce consistency between fragments.
    //  Currently we compile all related fragments together (we don't do klib for common separately), so we have to use
    //  consistent compiler arguments. This is why we forbid configurations where some fragments diverge.
    private fun List<Fragment>.mergedKotlinSettings(): KotlinUserSettings = KotlinUserSettings(
        languageVersion = unanimousKotlinSetting("languageVersion") { it.languageVersion },
        apiVersion = unanimousKotlinSetting("apiVersion") { it.apiVersion },
        allWarningsAsErrors = unanimousKotlinSetting("allWarningsAsErrors") { it.allWarningsAsErrors },
        suppressWarnings = unanimousKotlinSetting("suppressWarnings") { it.suppressWarnings },
        verbose = unanimousKotlinSetting("verbose") { it.verbose },
        progressiveMode = unanimousKotlinSetting("progressiveMode") { it.progressiveMode },
        languageFeatures = unanimousOptionalKotlinSetting("languageFeatures") { it.languageFeatures } ?: emptyList(),
        optIns = unanimousOptionalKotlinSetting("optIns") { it.optIns } ?: emptyList(),
        freeCompilerArgs = unanimousOptionalKotlinSetting("freeCompilerArgs") { it.freeCompilerArgs } ?: emptyList(),
        serialization = unanimousOptionalKotlinSetting("serialization.format") { it.serialization?.format },
    )

    private fun <T : Any> List<Fragment>.unanimousOptionalKotlinSetting(settingFqn: String, selector: (KotlinSettings) -> T?): T? =
        unanimousSetting("kotlin.$settingFqn") { selector(it.kotlin) }

    private fun <T : Any> List<Fragment>.unanimousKotlinSetting(settingFqn: String, selector: (KotlinSettings) -> T): T =
        unanimousSetting("kotlin.$settingFqn") { selector(it.kotlin) } ?:
            error("Module '${module.userReadableName}' has no fragments, cannot merge Kotlin setting '$settingFqn'")

    private fun <T> List<Fragment>.unanimousSetting(settingFqn: String, selector: (Settings) -> T): T? {
        val distinctValues = mapNotNull { selector(it.settings) }.distinct()
        if (distinctValues.size > 1) {
            error("The fragments ${userReadableList()} of module '${module.userReadableName}' are compiled " +
                    "together but provide several different values for 'settings.$settingFqn': $distinctValues")
        }
        return distinctValues.singleOrNull()
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

        val compilerPlugins = mutableListOf<Path>()
        if (!kotlinUserSettings.serialization.isNullOrEmpty()) {
            val plugin = kotlinCompilerDownloader.downloadKotlinSerializationPlugin(compilerVersion)
            compilerPlugins.add(plugin)
        }

        val compilerArgs = kotlinCompilerArgs(
            isMultiplatform = isMultiplatform,
            kotlinUserSettings = kotlinUserSettings,
            classpath = classpath,
            jdkHome = jdkHome,
            outputPath = taskOutputRoot.path,
            compilerPlugins = compilerPlugins,
            friendlyClasspath = friendlyClasspath,
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
