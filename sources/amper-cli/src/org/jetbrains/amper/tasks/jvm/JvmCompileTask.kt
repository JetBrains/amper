/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.tasks.jvm

import io.opentelemetry.api.trace.SpanBuilder
import kotlinx.serialization.json.Json
import org.jetbrains.amper.BuildPrimitives
import org.jetbrains.amper.cli.AmperBuildOutputRoot
import org.jetbrains.amper.cli.AmperProjectRoot
import org.jetbrains.amper.cli.AmperProjectTempRoot
import org.jetbrains.amper.cli.telemetry.setAmperModule
import org.jetbrains.amper.cli.telemetry.setFragments
import org.jetbrains.amper.cli.userReadableError
import org.jetbrains.amper.compilation.CompilationUserSettings
import org.jetbrains.amper.compilation.ErrorsCollectorKotlinLogger
import org.jetbrains.amper.compilation.JavaUserSettings
import org.jetbrains.amper.compilation.KotlinArtifactsDownloader
import org.jetbrains.amper.compilation.asKotlinLogger
import org.jetbrains.amper.compilation.downloadCompilerPlugins
import org.jetbrains.amper.compilation.kotlinJvmCompilerArgs
import org.jetbrains.amper.compilation.loadMaybeCachedImpl
import org.jetbrains.amper.compilation.plus
import org.jetbrains.amper.compilation.serializableCompilationSettings
import org.jetbrains.amper.compilation.singleLeafFragment
import org.jetbrains.amper.compilation.toKotlinProjectId
import org.jetbrains.amper.core.AmperUserCacheRoot
import org.jetbrains.amper.core.extract.cleanDirectory
import org.jetbrains.amper.engine.BuildTask
import org.jetbrains.amper.engine.TaskGraphExecutionContext
import org.jetbrains.amper.frontend.AmperModule
import org.jetbrains.amper.frontend.Fragment
import org.jetbrains.amper.frontend.Platform
import org.jetbrains.amper.frontend.TaskName
import org.jetbrains.amper.frontend.aomBuilder.javaAnnotationProcessingGeneratedSourcesPath
import org.jetbrains.amper.frontend.jdkSettings
import org.jetbrains.amper.incrementalcache.IncrementalCache
import org.jetbrains.amper.jdk.provisioning.Jdk
import org.jetbrains.amper.jdk.provisioning.JdkProvider
import org.jetbrains.amper.jvm.getJdkOrUserError
import org.jetbrains.amper.processes.LoggingProcessOutputListener
import org.jetbrains.amper.processes.withJavaArgFile
import org.jetbrains.amper.tasks.AdditionalClasspathProvider
import org.jetbrains.amper.tasks.CommonTaskUtils.userReadableList
import org.jetbrains.amper.tasks.ResolveExternalDependenciesTask
import org.jetbrains.amper.tasks.SourceRoot
import org.jetbrains.amper.tasks.TaskResult
import org.jetbrains.amper.tasks.artifacts.ArtifactTaskBase
import org.jetbrains.amper.tasks.artifacts.JvmResourcesDirArtifact
import org.jetbrains.amper.tasks.artifacts.KotlinJavaSourceDirArtifact
import org.jetbrains.amper.tasks.artifacts.Selectors
import org.jetbrains.amper.tasks.artifacts.api.Quantifier
import org.jetbrains.amper.tasks.identificationPhrase
import org.jetbrains.amper.tasks.java.JavaAnnotationProcessorClasspathTask
import org.jetbrains.amper.tasks.maven.MavenPhaseResult
import org.jetbrains.amper.telemetry.setListAttribute
import org.jetbrains.amper.telemetry.spanBuilder
import org.jetbrains.amper.telemetry.use
import org.jetbrains.amper.util.BuildType
import org.jetbrains.kotlin.buildtools.api.CompilationResult
import org.jetbrains.kotlin.buildtools.api.CompilationService
import org.jetbrains.kotlin.buildtools.api.ExperimentalBuildToolsApi
import org.slf4j.LoggerFactory
import java.io.File
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.div
import kotlin.io.path.exists
import kotlin.io.path.extension
import kotlin.io.path.isDirectory
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.pathString
import kotlin.io.path.walk

@OptIn(ExperimentalBuildToolsApi::class)
internal class JvmCompileTask(
    override val module: AmperModule,
    override val isTest: Boolean,
    private val fragments: List<Fragment>,
    private val userCacheRoot: AmperUserCacheRoot,
    private val projectRoot: AmperProjectRoot,
    private val tempRoot: AmperProjectTempRoot,
    override val taskName: TaskName,
    private val incrementalCache: IncrementalCache,
    private val kotlinArtifactsDownloader: KotlinArtifactsDownloader =
        KotlinArtifactsDownloader(userCacheRoot, incrementalCache),
    private val buildOutputRoot: AmperBuildOutputRoot,
    private val jdkProvider: JdkProvider,
    override val buildType: BuildType? = null,
    override val platform: Platform = Platform.JVM,
): ArtifactTaskBase(), BuildTask {

    init {
        require(platform == Platform.JVM || platform == Platform.ANDROID) {
            "Illegal platform for JvmCompileTask: $platform"
        }
    }

    private val compiledJvmClassesPath by CompiledJvmClassesArtifact(
        buildOutputRoot = buildOutputRoot,
        module = module,
        platform = platform,
        isTest = isTest,
        buildType = buildType,
    )

    private val additionalKotlinJavaSourceDirs by Selectors.fromMatchingFragments(
        type = KotlinJavaSourceDirArtifact::class,
        module = module,
        isTest = isTest,
        hasPlatforms = setOf(platform),
        quantifier = Quantifier.AnyOrNone,
    )

    private val additionalResourcesDirs by Selectors.fromMatchingFragments(
        type = JvmResourcesDirArtifact::class,
        module = module,
        isTest = isTest,
        hasPlatforms = setOf(platform),
        quantifier = Quantifier.AnyOrNone,
    )

    val taskOutputRoot get() = compiledJvmClassesPath.path

    private val javaCompilerOutputRoot = taskOutputRoot / "java-output"
    private val kotlinCompilerOutputRoot = taskOutputRoot / "kotlin-output"
    private val resourcesRoot = taskOutputRoot / "resources-output"
    private val jicDataDir = taskOutputRoot / "jic-data"

    override suspend fun run(dependenciesResult: List<TaskResult>, executionContext: TaskGraphExecutionContext): TaskResult {
        require(fragments.isNotEmpty()) {
            "fragments list is empty for jvm compile task, module=${module.userReadableName}"
        }

        logger.debug("compile ${module.userReadableName} -- ${fragments.userReadableList()}")

        val mavenDependencies = dependenciesResult
            .filterIsInstance<ResolveExternalDependenciesTask.Result>()
            .singleOrNull()
            ?: error("Expected one and only one dependency on (${ResolveExternalDependenciesTask.Result::class.java.simpleName}) input, but got: ${dependenciesResult.joinToString { it.javaClass.simpleName }}")

        val compileModuleDependencies = dependenciesResult.filterIsInstance<Result>()
        val javaAnnotationProcessorClasspath = dependenciesResult
            .filterIsInstance<JavaAnnotationProcessorClasspathTask.Result>()
            .singleOrNull()
            ?.processorClasspath
            ?: emptyList()

        val productionJvmCompileResult = if (isTest) {
            compileModuleDependencies.firstOrNull { it.module == module && !it.isTest }
                ?: error("jvm compilation result from production compilation result was not found for module=${module.userReadableName}, task=$taskName")
        } else null

        val userSettings = fragments.singleLeafFragment().serializableCompilationSettings()

        val additionalClasspath = dependenciesResult.filterIsInstance<AdditionalClasspathProvider>().flatMap { it.compileClasspath }
        val classpath =
            compileModuleDependencies.flatMap { it.classesOutputRoots } + mavenDependencies.compileClasspath + additionalClasspath

        // Collect additional source roots.
        val additionalArtifactSources = additionalKotlinJavaSourceDirs.map { artifact ->
            SourceRoot(
                fragmentName = artifact.fragmentName,
                path = artifact.path,
            )
        }
        val additionalSourceRootsFromMaven = dependenciesResult
            .filterIsInstance<MavenPhaseResult>()
            .flatMap { it.sourceRoots }
            .distinctBy { it.path } // Need to remove duplicates, because a same path can be provided by multiple providers.
        
        val additionalSources = additionalArtifactSources + additionalSourceRootsFromMaven

        val additionalResources = additionalResourcesDirs.map { artifact ->
            SourceRoot(
                fragmentName = artifact.fragmentName,
                path = artifact.path,
            )
        }

        val jdk = jdkProvider.getJdkOrUserError(jdkSettings = module.jdkSettings)

        val javaAnnotationProcessorsGeneratedDir =
            fragments.singleLeafFragment().javaAnnotationProcessingGeneratedSourcesPath(buildOutputRoot.path)

        val inputValues = mapOf(
            "jdk.version" to jdk.version,
            "jdk.home" to jdk.homeDir.pathString,
            "user.settings" to Json.encodeToString(userSettings),
            "task.output.root" to taskOutputRoot.pathString,
            "target.platforms" to module.leafPlatforms.map { it.name }.sorted().joinToString(),
            "java.annotation.processor.generated.dir" to javaAnnotationProcessorsGeneratedDir.pathString
        )

        val sources = fragments.flatMap { it.sourceRoots }.map { it.toAbsolutePath() } + additionalSources.map { it.path }
        val resources = fragments.map { it.resourcesPath }.map { it.toAbsolutePath() } + additionalResources.map { it.path }
        val inputFiles = sources + resources + classpath + javaAnnotationProcessorClasspath

        val result = incrementalCache.execute(taskName.name, inputValues, inputFiles) {
            cleanDirectory(javaAnnotationProcessorsGeneratedDir)
            if (!shouldCompileJavaIncrementally(userSettings.java, javaAnnotationProcessorClasspath)) {
                cleanDirectory(taskOutputRoot)
            }
            javaCompilerOutputRoot.createDirectories()
            kotlinCompilerOutputRoot.createDirectories()
            resourcesRoot.createDirectories()

            val nonEmptySourceDirs = sources
                .filter {
                    when {
                        it.isDirectory() -> it.listDirectoryEntries().isNotEmpty()
                        it.exists() ->
                            error("Source directory at '$it' exists, but it's not a directory, this is currently unsupported")
                        else -> false
                    }
                }

            val outputPaths = mutableListOf<Path>()
            outputPaths.add(javaCompilerOutputRoot.toAbsolutePath())
            outputPaths.add(kotlinCompilerOutputRoot.toAbsolutePath())

            if (nonEmptySourceDirs.isNotEmpty()) {
                compileSources(
                    jdk = jdk,
                    sourceDirectories = nonEmptySourceDirs,
                    additionalSources = additionalSources,
                    userSettings = userSettings,
                    classpath = classpath,
                    friendPaths = productionJvmCompileResult?.classesOutputRoots.orEmpty(),
                    javaAnnotationProcessorClasspath = javaAnnotationProcessorClasspath,
                    javaAnnotationProcessorsGeneratedDir = javaAnnotationProcessorsGeneratedDir,
                    tempRoot = tempRoot,
                )
                if (javaAnnotationProcessorsGeneratedDir.exists()) {
                    outputPaths.add(javaAnnotationProcessorsGeneratedDir)
                }
            } else {
                logger.debug("No sources were found for ${fragments.identificationPhrase()}, skipping compilation")
            }

            val presentResources = resources.filter { it.exists() }
            for (resource in presentResources) {
                val dest = if (resource.isDirectory()) {
                    resourcesRoot
                } else {
                    resourcesRoot / resource.fileName
                }
                logger.debug("Copying resources from '{}' to '{}'...", resource, dest)

                // if we compile incrementally, then we don't clean the output dir => overwrite instead of failing that a file exists
                val overwrite = shouldCompileJavaIncrementally(userSettings.java, javaAnnotationProcessorClasspath)
                BuildPrimitives.copy(from = resource, to = dest, overwrite = overwrite)
            }

            return@execute IncrementalCache.ExecutionResult(outputPaths)
        }

        return Result(
            classesOutputRoots = listOf(
                javaCompilerOutputRoot,
                kotlinCompilerOutputRoot,
                resourcesRoot
            ).map { it.toAbsolutePath() },
            module = module,
            isTest = isTest,
            changes = result.changes,
        )
    }

    private suspend fun compileSources(
        jdk: Jdk,
        sourceDirectories: List<Path>,
        additionalSources: List<SourceRoot>,
        userSettings: CompilationUserSettings,
        classpath: List<Path>,
        friendPaths: List<Path>,
        javaAnnotationProcessorClasspath: List<Path>,
        tempRoot: AmperProjectTempRoot,
        javaAnnotationProcessorsGeneratedDir: Path,
    ) {
        for (friendPath in friendPaths) {
            require(classpath.contains(friendPath)) {
                "The classpath must contain all friend paths, but '$friendPath' is not in '${classpath.joinToString(File.pathSeparator)}'"
            }
        }

        val sourcesFiles = sourceDirectories.flatMap { it.walk() }
        val kotlinFilesToCompile = sourcesFiles.filter { it.extension == "kt" }
        val javaFilesToCompile = sourcesFiles.filter { it.extension == "java" }

        if (kotlinFilesToCompile.isNotEmpty()) {
            // Enable multi-platform support only if targeting other than JVM platforms
            // or having a common and JVM fragments (like src and src@jvm directories)
            val isMultiplatform = (module.leafPlatforms - Platform.JVM).isNotEmpty() || sourceDirectories.size > 1

            compileKotlinSources(
                userSettings = userSettings,
                isMultiplatform = isMultiplatform,
                classpath = classpath,
                jdk = jdk,
                sourceFiles = kotlinFilesToCompile + javaFilesToCompile,
                additionalSourceRoots = additionalSources,
                friendPaths = friendPaths,
            )
        }

        if (javaFilesToCompile.isNotEmpty()) {
            compileJavaSources(
                jdk = jdk,
                userSettings = userSettings,
                classpath = classpath + listOf(kotlinCompilerOutputRoot),
                processorClasspath = javaAnnotationProcessorClasspath,
                processorGeneratedDir = javaAnnotationProcessorsGeneratedDir,
                javaSourceFiles = javaFilesToCompile,
                tempRoot = tempRoot,
            )
        }
    }

    private suspend fun compileKotlinSources(
        userSettings: CompilationUserSettings,
        isMultiplatform: Boolean,
        classpath: List<Path>,
        jdk: Jdk,
        sourceFiles: List<Path>,
        additionalSourceRoots: List<SourceRoot>,
        friendPaths: List<Path>,
    ) {
        val compilationService = CompilationService.loadMaybeCachedImpl(
            kotlinVersion = userSettings.kotlin.compilerVersion,
            downloader = kotlinArtifactsDownloader,
        )

        // TODO should we allow users to choose in-process vs daemon?
        // TODO settings for daemon JVM args?
        // FIXME Daemon strategy currently fails with "Can't get connection"
        val executionConfig = compilationService.makeCompilerExecutionStrategyConfiguration()
            .useInProcessStrategy()
            //.useDaemonStrategy(jvmArguments = emptyList())

        // TODO configure incremental compilation here
        val errorsCollector = ErrorsCollectorKotlinLogger()
        val compilationConfig = compilationService.makeJvmCompilationConfiguration()
            .useLogger(logger.asKotlinLogger() + errorsCollector)

        val compilerPlugins = kotlinArtifactsDownloader.downloadCompilerPlugins(userSettings.kotlin.compilerPlugins)

        val compilerArgs = kotlinJvmCompilerArgs(
            isMultiplatform = isMultiplatform,
            userSettings = userSettings,
            classpath = classpath,
            jdkHome = jdk.homeDir,
            outputPath = kotlinCompilerOutputRoot,
            compilerPlugins = compilerPlugins,
            fragments = fragments,
            additionalSourceRoots = additionalSourceRoots,
            friendPaths = friendPaths,
        )

        val kotlinCompilationResult = spanBuilder("kotlin-compilation")
            .setAmperModule(module)
            .setFragments(fragments)
            .setListAttribute("source-files", sourceFiles.map { it.pathString })
            .setListAttribute("compiler-args", compilerArgs)
            .setAttribute("compiler-version", userSettings.kotlin.compilerVersion)
            .use {
                logger.info("Compiling module '${module.userReadableName}' for platform '${platform.pretty}'...")
                // TODO capture compiler errors/warnings in span (currently stdout/stderr are only logged)
                val projectId = projectRoot.toKotlinProjectId()
                val compilationResult = compilationService.compileJvm(
                    projectId = projectId,
                    strategyConfig = executionConfig,
                    compilationConfig = compilationConfig,
                    sources = sourceFiles.map { it.toFile() },
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
        processorClasspath: List<Path>,
        javaSourceFiles: List<Path>,
        tempRoot: AmperProjectTempRoot,
        processorGeneratedDir: Path,
    ) {
        // javac arguments that are common for plain javac and JIC
        val commonArgs = buildList {
            if (userSettings.jvmRelease != null) {
                add("--release")
                add(userSettings.jvmRelease.releaseNumber.toString())
            }

            if (userSettings.java.parameters) {
                add("-parameters")
            }

            // necessary for reproducible source jars across OS-es
            add("-encoding")
            add("utf-8")

            // TODO Should we move settings.kotlin.debug to settings.jvm.debug and use it here?
            add("-g")

            if (!processorClasspath.isEmpty()) {
                val annotationProcessorArgs = buildAnnotationProcessorArgs(userSettings.java, processorClasspath, processorGeneratedDir)
                addAll(annotationProcessorArgs)
            }
        }

        val freeCompilerArgs = userSettings.java.freeCompilerArgs

        val success = if (shouldCompileJavaIncrementally(userSettings.java, processorClasspath)) {
            jicDataDir.createDirectories()
            val jicJavacArgs = commonArgs + freeCompilerArgs
            javacSpanBuilder(jicJavacArgs, jdk, incremental = true).use {
                compileJavaWithJic(
                    jdk,
                    module,
                    isTest,
                    javaSourceFiles,
                    jicJavacArgs,
                    javaCompilerOutputRoot,
                    jicDataDir,
                    classpath,
                    logger
                )
            }
        } else {
            compileJavaWithPlainJavac(tempRoot, jdk, commonArgs, classpath, freeCompilerArgs, javaSourceFiles)
        }
        if (!success) {
            userReadableError("Java compilation failed (see errors above)")
        }
    }

    private suspend fun compileJavaWithPlainJavac(
        tempRoot: AmperProjectTempRoot,
        jdk: Jdk,
        commonJavacArgs: List<String>,
        classpath: List<Path>,
        freeCompilerArgs: List<String>,
        javaSourceFiles: List<Path>,
    ): Boolean {
        val plainJavacArgs = buildList {
            addAll(commonJavacArgs)

            add("-classpath")
            add(classpath.joinToString(File.pathSeparator))

            // https://blog.ltgt.net/most-build-tools-misuse-javac/
            // we compile module by module, so we don't need javac lookup into other modules
            add("-sourcepath")
            add(":")
            add("-implicit:none")

            add("-d")
            add(javaCompilerOutputRoot.pathString)

            addAll(freeCompilerArgs)

            addAll(javaSourceFiles.map { it.pathString })
        }

        val exitCode = withJavaArgFile(tempRoot, plainJavacArgs) { argsFile ->
            val result = javacSpanBuilder(plainJavacArgs, jdk, incremental = false).use { span ->
                BuildPrimitives.runProcessAndGetOutput(
                    workingDir = jdk.homeDir,
                    command = listOf(jdk.javacExecutable.pathString, "@${argsFile.pathString}"),
                    span = span,
                    outputListener = LoggingProcessOutputListener(logger),
                )
            }
            result.exitCode
        }
        return exitCode == 0
    }

    private fun javacSpanBuilder(args: List<String>, jdk: Jdk, incremental: Boolean): SpanBuilder {
        return spanBuilder("javac")
            .setAttribute("incremental", incremental)
            .setAmperModule(module)
            .setListAttribute("args", args)
            .setAttribute("jdk-home", jdk.homeDir.pathString)
            .setAttribute("version", jdk.version)
    }

    fun shouldCompileJavaIncrementally(javaUserSettings: JavaUserSettings, javaAnnotationProcessorsClassPath: List<Path>): Boolean {
        if (javaAnnotationProcessorsClassPath.isNotEmpty()) {
            // annotation processors are not supported by JPS yet
            return false
        }
        return javaUserSettings.compileIncrementally || System.getProperty("org.jetbrains.amper.jic").toBoolean()
    }

    class Result(
        val classesOutputRoots: List<Path>,
        val module: AmperModule,
        val isTest: Boolean,
        val changes: List<IncrementalCache.Change>,
    ) : TaskResult, RuntimeClasspathElementProvider {
        override val paths: List<Path>
            get() = classesOutputRoots
    }

    private val logger = LoggerFactory.getLogger(javaClass)
}

private fun buildAnnotationProcessorArgs(
    javaSettings: JavaUserSettings,
    processorClasspath: List<Path>,
    generatedSourcesDir: Path,
): List<String> = buildList {
    add("-processorpath")
    add(processorClasspath.joinToString(File.pathSeparator))

    // Add generated sources directory
    add("-s")
    add(generatedSourcesDir.pathString)

    javaSettings.annotationProcessorOptions.forEach { (key, value) ->
        add("-A$key=$value")
    }
}
