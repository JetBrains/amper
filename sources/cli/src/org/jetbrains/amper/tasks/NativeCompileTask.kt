/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.tasks

import org.jetbrains.amper.BuildPrimitives
import org.jetbrains.amper.cli.AmperProjectTempRoot
import org.jetbrains.amper.cli.AmperUserCacheRoot
import org.jetbrains.amper.cli.JdkDownloader
import org.jetbrains.amper.cli.userReadableError
import org.jetbrains.amper.compilation.KotlinCompilerDownloader
import org.jetbrains.amper.compilation.withKotlinCompilerArgFile
import org.jetbrains.amper.diagnostics.setAmperModule
import org.jetbrains.amper.diagnostics.setListAttribute
import org.jetbrains.amper.diagnostics.spanBuilder
import org.jetbrains.amper.diagnostics.useWithScope
import org.jetbrains.amper.downloader.cleanDirectory
import org.jetbrains.amper.engine.TaskName
import org.jetbrains.amper.frontend.Platform
import org.jetbrains.amper.frontend.PotatoModule
import org.jetbrains.amper.tasks.CommonTaskUtils.userReadableList
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
    private val module: PotatoModule,
    override val platform: Platform,
    private val userCacheRoot: AmperUserCacheRoot,
    private val taskOutputRoot: TaskOutputRoot,
    private val executeOnChangedInputs: ExecuteOnChangedInputs,
    override val taskName: TaskName,
    private val tempRoot: AmperProjectTempRoot,
    private val isTest: Boolean,
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

        val compiledModuleDependencies = dependenciesResult
            .filterIsInstance<TaskResult>()
            .flatMap { it.walkDependenciesRecursively<TaskResult>() + it }
            .map { it.artifact }

        // todo native resources are what exactly?

        // TODO dependencies support
        // TODO kotlin version settings
        val kotlinVersion = KotlinCompilerDownloader.AMPER_DEFAULT_KOTLIN_VERSION

        // TODO do in separate (and cacheable) task
        val kotlinNativeHome = kotlinCompilerDownloader.downloadAndExtractKotlinNative(kotlinVersion)
            ?: error("kotlin native compiler is not available at this platform")

        val ext = if (org.jetbrains.amper.util.OS.isWindows) ".bat" else ""
        val konancExecutable = kotlinNativeHome.resolve("bin").resolve("konanc$ext")
        if (!konancExecutable.isExecutable()) {
            error("kotlin native home does not have konanc executable at $konancExecutable")
        }

        logger.info("native compile ${module.userReadableName} -- ${fragments.joinToString(" ") { it.name }}")

        // TODO this is JDK to run konanc, what are the requirements?
        val jdkHome = JdkDownloader.getJdkHome(userCacheRoot)
        val javaExecutable = JdkDownloader.getJavaExecutable(jdkHome)

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
            "entry.point" to (entryPoint ?: ""),
            "task.output.root" to taskOutputRoot.path.pathString,
        )

        val inputs = fragments.map { it.src } + compiledModuleDependencies
        val artifact = executeOnChangedInputs.execute(taskName.name, configuration, inputs) {
            cleanDirectory(taskOutputRoot.path)

            val artifactExtension = when {
                module.type.isLibrary() && !isTest -> ".klib"
                OS.isWindows -> ".exe"
                else -> ".kexe"
            }
            val artifact = taskOutputRoot.path.resolve(module.userReadableName + artifactExtension)

            val args = mutableListOf(
                "-g",
                "-ea",
                "-produce", if (module.type.isLibrary() && !isTest) "library" else "program",
                // TODO full module path including entire hierarchy? -Xshort-module-name?
                "-module-name", module.userReadableName,
                "-output", artifact.pathString,
                "-target", platform.name.lowercase(),
                "-Xmulti-platform",
            )

            if (isTest) {
                args.add("-generate-test-runner")
            }

            if (entryPoint != null) {
                args.add("-entry")
                args.add(entryPoint)
            }

            args.addAll(compiledModuleDependencies.flatMap { listOf("-l", it.pathString) })

            val tempFilesToDelete = mutableListOf<Path>()

            try {
                val existingSourceRoots = fragments.map { it.src }.filter { it.exists() }
                val rootsToCompile = existingSourceRoots.ifEmpty {
                    // konanc does not want to compile application with zero sources files,
                    // but it's a perfectly valid situation where all code is in shared library
                    val emptyKotlinFile = Files.createTempFile(tempRoot.path, "empty", ".kt")
                        .also { tempFilesToDelete.add(it) }
                    listOf(emptyKotlinFile)
                }

                args.addAll(rootsToCompile.map { it.pathString })

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
