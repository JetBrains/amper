/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.tasks.native

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.jetbrains.amper.BuildPrimitives
import org.jetbrains.amper.cli.AmperProjectTempRoot
import org.jetbrains.amper.cli.AmperUserCacheRoot
import org.jetbrains.amper.cli.JdkDownloader
import org.jetbrains.amper.cli.userReadableError
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
import org.jetbrains.amper.tasks.CommonTaskUtils.userReadableList
import org.jetbrains.amper.tasks.CompileTask
import org.jetbrains.amper.tasks.ResolveExternalDependenciesTask
import org.jetbrains.amper.tasks.TaskOutputRoot
import org.jetbrains.amper.tasks.TaskResult.Companion.walkDependenciesRecursively
import org.jetbrains.amper.util.ExecuteOnChangedInputs
import org.jetbrains.amper.util.OS
import org.jetbrains.amper.util.ShellQuoting
import org.slf4j.LoggerFactory
import java.io.File
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
    override val isTest: Boolean,
    private val kotlinCompilerDownloader: KotlinCompilerDownloader =
        KotlinCompilerDownloader(userCacheRoot, executeOnChangedInputs),
): CompileTask {
    init {
        require(platform.isLeaf)
        require(platform.topmostParentNoCommon == Platform.NATIVE)
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
            .filterIsInstance<ResolveExternalDependenciesTask.TaskResult>()
            .singleOrNull()
            ?: error("Expected one and only one dependency on (${ResolveExternalDependenciesTask.TaskResult::class.java.simpleName}) input, but got: ${dependenciesResult.joinToString { it.javaClass.simpleName }}")

        // TODO Native compiler won't work without recursive dependencies, which we should correctly calculate in that case
        val externalDependencies = (externalDependenciesTaskResult.compileClasspath +
                dependenciesResult.flatMap {
                    result -> result.walkDependenciesRecursively<ResolveExternalDependenciesTask.TaskResult>().flatMap { it.compileClasspath }
                })
            .distinct()
        logger.warn("native compile ${module.userReadableName} -- collected external dependencies\n${externalDependencies.sorted().joinToString("\n").prependIndent("  ")}")

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
        val javaExecutable = JdkDownloader.getJdk(userCacheRoot).javaExecutable

        val entryPoints = if (module.type.isApplication()) {
            fragments.mapNotNull { it.settings.native?.entryPoint }.distinct()
        } else emptyList()
        if (entryPoints.size > 1) {
            error("Multiple entry points defined for module ${module.userReadableName} fragments ${fragments.userReadableList()}: ${entryPoints.joinToString()}")
        }
        val entryPoint = entryPoints.singleOrNull()

        val configuration: Map<String, String> = mapOf(
            "konanc.jre.url" to JdkDownloader.currentSystemFixedJdkUrl.toString(),
            "kotlin.version" to kotlinVersion,
            "kotlin.settings" to Json.encodeToString(kotlinUserSettings),
            "entry.point" to (entryPoint ?: ""),
            "task.output.root" to taskOutputRoot.path.pathString,
        )

        val inputs = fragments.map { it.src } + compiledModuleDependencies + externalDependencies
        val artifact = executeOnChangedInputs.execute(taskName.name, configuration, inputs) {
            cleanDirectory(taskOutputRoot.path)

            val artifactExtension = when {
                module.type.isLibrary() && !isTest -> ".klib"
                platform.isDescendantOf(Platform.MINGW) -> ".exe"
                else -> ".kexe"
            }
            val artifact = taskOutputRoot.path.resolve(module.userReadableName + artifactExtension)

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
                )

                withKotlinCompilerArgFile(args, tempRoot) { argFile ->
                    // todo in the future we'll switch to kotlin tooling api and remove this awful code

                    val konanLib = kotlinNativeHome / "konan" / "lib"
                    val jvmArgs = listOf(
                        javaExecutable.pathString,
                        // from bin/run_konan
                        "-ea",
                        "-XX:TieredStopAtLevel=1",
                        "-Dfile.encoding=UTF-8",
                        "-Dkonan.home=$kotlinNativeHome",
                        "-cp",
                        listOf(konanLib / "kotlin-native-compiler-embeddable.jar",
                            konanLib / "trove4j.jar").joinToString(File.pathSeparator) { it.pathString },
                        "org.jetbrains.kotlin.cli.utilities.MainKt",
                        "konanc",
                        "@${argFile}",
                    )

                    spanBuilder("konanc")
                        .setAmperModule(module)
                        .setListAttribute("args", args)
                        .setAttribute("version", kotlinVersion)
                        .useWithScope { span ->
                            logger.info("Calling konanc ${ShellQuoting.quoteArgumentsPosixShellWay(args)}")
                            val result = BuildPrimitives.runProcessAndGetOutput(jvmArgs, kotlinNativeHome, span)
                            if (result.exitCode != 0) {
                                userReadableError("Kotlin native compilation failed (see errors above)")
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
        )
    }

    class TaskResult(
        override val dependencies: List<org.jetbrains.amper.tasks.TaskResult>,
        val artifact: Path,
    ) : org.jetbrains.amper.tasks.TaskResult

    private val logger = LoggerFactory.getLogger(javaClass)
}
