/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.tasks.native

import com.github.ajalt.mordant.terminal.Terminal
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.jetbrains.amper.cli.AmperProjectTempRoot
import org.jetbrains.amper.cli.AmperUserCacheRoot
import org.jetbrains.amper.cli.JdkDownloader
import org.jetbrains.amper.cli.userReadableError
import org.jetbrains.amper.compilation.KotlinCompilationType
import org.jetbrains.amper.compilation.KotlinCompilerDownloader
import org.jetbrains.amper.compilation.downloadCompilerPlugins
import org.jetbrains.amper.compilation.kotlinNativeCompilerArgs
import org.jetbrains.amper.compilation.mergedKotlinSettings
import org.jetbrains.amper.compilation.withKotlinCompilerArgFile
import org.jetbrains.amper.core.UsedVersions
import org.jetbrains.amper.core.extract.cleanDirectory
import org.jetbrains.amper.diagnostics.setAmperModule
import org.jetbrains.amper.diagnostics.setListAttribute
import org.jetbrains.amper.diagnostics.spanBuilder
import org.jetbrains.amper.diagnostics.useWithScope
import org.jetbrains.amper.engine.TaskName
import org.jetbrains.amper.frontend.Platform
import org.jetbrains.amper.frontend.PotatoModule
import org.jetbrains.amper.frontend.isDescendantOf
import org.jetbrains.amper.processes.ProcessOutputListener
import org.jetbrains.amper.processes.runJava
import org.jetbrains.amper.processes.setProcessResultAttributes
import org.jetbrains.amper.tasks.CommonTaskUtils.userReadableList
import org.jetbrains.amper.tasks.BuildTask
import org.jetbrains.amper.tasks.ProjectTasksBuilder
import org.jetbrains.amper.tasks.ResolveExternalDependenciesTask
import org.jetbrains.amper.tasks.TaskOutputRoot
import org.jetbrains.amper.tasks.TaskResult.Companion.walkDependenciesRecursively
import org.jetbrains.amper.util.ExecuteOnChangedInputs
import org.jetbrains.amper.util.OS
import org.jetbrains.amper.util.ShellQuoting
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.deleteExisting
import kotlin.io.path.div
import kotlin.io.path.exists
import kotlin.io.path.isExecutable
import kotlin.io.path.pathString

class NativeCompileTask(
    override val module: PotatoModule,
    override val platform: Platform,
    private val userCacheRoot: AmperUserCacheRoot,
    private val taskOutputRoot: TaskOutputRoot,
    private val executeOnChangedInputs: ExecuteOnChangedInputs,
    override val taskName: TaskName,
    private val tempRoot: AmperProjectTempRoot,
    private val terminal: Terminal,
    override val isTest: Boolean,
    val compilationType: KotlinCompilationType? = null,
    private val kotlinCompilerDownloader: KotlinCompilerDownloader =
        KotlinCompilerDownloader(userCacheRoot, executeOnChangedInputs),
): BuildTask {
    init {
        require(platform.isLeaf)
        require(platform.isDescendantOf(Platform.NATIVE))
    }

    override suspend fun run(dependenciesResult: List<org.jetbrains.amper.tasks.TaskResult>): TaskResult {
        val fragments = module.fragments.filter {
            it.platforms.contains(platform) && it.isTest == isTest
        }
        if (fragments.isEmpty()) {
            error("Zero fragments in module ${module.userReadableName} for platform $platform isTest=$isTest")
        }

        // TODO exported dependencies. It's better to supported them in unified way across JVM and Native

        val externalDependenciesTaskResult = dependenciesResult
            .filterIsInstance<ResolveExternalDependenciesTask.Result>()
            .singleOrNull()
            ?: error("Expected one and only one dependency on (${ResolveExternalDependenciesTask.Result::class.java.simpleName}) input, but got: ${dependenciesResult.joinToString { it.javaClass.simpleName }}")

        // TODO Native compiler won't work without recursive dependencies, which we should correctly calculate in that case
        val externalDependencies = (externalDependenciesTaskResult.compileClasspath +
                dependenciesResult.flatMap {
                    result -> result.walkDependenciesRecursively<ResolveExternalDependenciesTask.Result>().flatMap { it.compileClasspath }
                })
            .distinct()
        logger.warn("" +
                "native compile ${module.userReadableName} -- collected external dependencies" +
                if (externalDependencies.isNotEmpty()) "\n" else "" +
                externalDependencies.sorted().joinToString("\n").prependIndent("  ")
        )

        // TODO Rethink this approach.
        // Check if we are inside framework compilation, so there is connected dylib compilation.
        val compileSameModule = ProjectTasksBuilder.Companion.CommonTaskType.Compile.getTaskName(module, platform, isTest)
        val includeDependency = dependenciesResult
            .filterIsInstance<TaskResult>()
            .firstOrNull { it.taskName == compileSameModule }
        val includeArtifact = includeDependency?.artifact

        val compiledModuleDependencies = dependenciesResult
            .filterIsInstance<TaskResult>()
            .flatMap { it.walkDependenciesRecursively<TaskResult>() + it }
            .map { it.artifact }

        // todo native resources are what exactly?

        // TODO dependencies support
        // TODO kotlin version settings
        val kotlinVersion = UsedVersions.kotlinVersion
        val kotlinUserSettings = fragments.mergedKotlinSettings()

        // TODO do in separate (and cacheable) task
        val kotlinNativeHome = kotlinCompilerDownloader.downloadAndExtractKotlinNative(kotlinVersion)
            ?: error("kotlin native compiler is not available at this platform")

        val ext = if (OS.isWindows) ".bat" else ""
        val konancExecutable = kotlinNativeHome.resolve("bin").resolve("konanc$ext")
        if (!konancExecutable.isExecutable()) {
            error("kotlin native home does not have konanc executable at $konancExecutable")
        }

        logger.info("native compile ${module.userReadableName} -- ${fragments.joinToString(" ") { it.name }}")

        // TODO this the is JDK to run konanc, what are the requirements?
        val jdk = JdkDownloader.getJdk(userCacheRoot)

        val entryPoints = if (module.type.isApplication()) {
            fragments.mapNotNull { it.settings.native?.entryPoint }.distinct()
        } else emptyList()
        if (entryPoints.size > 1) {
            error("Multiple entry points defined for module ${module.userReadableName} fragments ${fragments.userReadableList()}: ${entryPoints.joinToString()}")
        }
        val entryPoint = entryPoints.singleOrNull()

        val configuration: Map<String, String> = mapOf(
            "konanc.jre.url" to jdk.downloadUrl.toString(),
            "kotlin.version" to kotlinVersion,
            "kotlin.settings" to Json.encodeToString(kotlinUserSettings),
            "entry.point" to (entryPoint ?: ""),
            "task.output.root" to taskOutputRoot.path.pathString,
        )

        val inputs = fragments.map { it.src } + compiledModuleDependencies + externalDependencies
        val artifact = executeOnChangedInputs.execute(taskName.name, configuration, inputs) {
            cleanDirectory(taskOutputRoot.path)

            val finalCompilationType = compilationType
                ?: if (module.type.isLibrary() && !isTest) KotlinCompilationType.LIBRARY else KotlinCompilationType.BINARY

            val artifact = finalCompilationType.output(taskOutputRoot.path, module, platform)

            val libraryPaths = compiledModuleDependencies + externalDependencies.filter { !it.pathString.endsWith(".jar") }

            val tempFilesToDelete = mutableListOf<Path>()

            val compilerPlugins = kotlinCompilerDownloader.downloadCompilerPlugins(kotlinVersion, kotlinUserSettings)
            try {
                val existingSourceRoots = fragments.map { it.src }.filter { it.exists() }
                val rootsToCompile = existingSourceRoots.ifEmpty {
                    // konanc does not want to compile application with zero sources files,
                    // but it's a perfectly valid situation where all code is in shared library
                    val emptyKotlinFile = Files.createTempFile(tempRoot.path, "empty", ".kt")
                        .also { tempFilesToDelete.add(it) }
                    listOf(emptyKotlinFile)
                }

                val args = kotlinNativeCompilerArgs(
                    kotlinUserSettings = kotlinUserSettings,
                    compilerPlugins = compilerPlugins,
                    entryPoint = entryPoint,
                    libraryPaths = libraryPaths,
                    sourceFiles = rootsToCompile,
                    outputPath = artifact,
                    compilationType = finalCompilationType,
                    include = includeArtifact
                )

                withKotlinCompilerArgFile(args, tempRoot) { argFile ->

                    spanBuilder("konanc")
                        .setAmperModule(module)
                        .setListAttribute("args", args)
                        .setAttribute("version", kotlinVersion)
                        .useWithScope { span ->
                            logger.info("Calling konanc ${ShellQuoting.quoteArgumentsPosixShellWay(args)}")

                            val konanLib = kotlinNativeHome / "konan" / "lib"

                            // We call konanc via java because the konanc command line doesn't support spaces in paths:
                            // https://youtrack.jetbrains.com/issue/KT-66952
                            // TODO in the future we'll switch to kotlin tooling api and remove this raw java exec anyway
                            val result = jdk.runJava(
                                workingDir = kotlinNativeHome,
                                mainClass = "org.jetbrains.kotlin.cli.utilities.MainKt",
                                classpath = listOf(
                                    konanLib / "kotlin-native-compiler-embeddable.jar",
                                    konanLib / "trove4j.jar",
                                ),
                                programArgs = listOf("konanc", "@${argFile}"),
                                // JVM args partially copied from <kotlinNativeHome>/bin/run_konan
                                jvmArgs = listOf(
                                    "-ea",
                                    "-XX:TieredStopAtLevel=1",
                                    "-Dfile.encoding=UTF-8",
                                    "-Dkonan.home=$kotlinNativeHome",
                                ),
                                outputListener = object : ProcessOutputListener {
                                    override fun onStdoutLine(line: String) {
                                        logger.info(line)
                                    }

                                    override fun onStderrLine(line: String) {
                                        logger.error(line)
                                    }
                                },
                            )

                            // TODO this is redundant with the java span of the external process run. Ideally, we
                            //  should extract higher-level information from the raw output and use that in this span.
                            span.setProcessResultAttributes(result)

                            if (result.exitCode != 0) {
                                val errors = result.stderr
                                    .lines()
                                    .filter { it.startsWith("error: ") || it.startsWith("exception: ")}
                                    .joinToString("\n")
                                val errorsPart = if (errors.isNotEmpty()) ":\n\n$errors" else ""
                                userReadableError("Kotlin native compilation failed$errorsPart")
                            }
                        }
                }
            } finally {
                for (tempPath in tempFilesToDelete) {
                    tempPath.deleteExisting()
                }
            }

            return@execute ExecuteOnChangedInputs.ExecutionResult(listOf(artifact))
        }.outputs.single()

        return TaskResult(
            dependencies = dependenciesResult,
            artifact = artifact,
            taskName = taskName,
        )
    }

    class TaskResult(
        override val dependencies: List<org.jetbrains.amper.tasks.TaskResult>,
        val artifact: Path,
        val taskName: TaskName,
    ) : org.jetbrains.amper.tasks.TaskResult

    private val logger = LoggerFactory.getLogger(javaClass)
}
