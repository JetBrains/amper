/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.tasks

import io.opentelemetry.api.common.AttributeKey
import org.jetbrains.amper.BuildPrimitives
import org.jetbrains.amper.cli.AmperProjectTempRoot
import org.jetbrains.amper.cli.AmperUserCacheRoot
import org.jetbrains.amper.cli.JdkDownloader
import org.jetbrains.amper.cli.KotlinCompilerUtil
import org.jetbrains.amper.cli.TaskName
import org.jetbrains.amper.diagnostics.spanBuilder
import org.jetbrains.amper.diagnostics.useWithScope
import org.jetbrains.amper.downloader.cleanDirectory
import org.jetbrains.amper.frontend.Platform
import org.jetbrains.amper.frontend.PotatoModule
import org.jetbrains.amper.util.ExecuteOnChangedInputs
import org.jetbrains.amper.util.ShellQuoting
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.isExecutable
import kotlin.io.path.pathString
import kotlin.io.path.writeText

class NativeCompileTask(
    private val module: PotatoModule,
    private val platform: Platform,
    private val userCacheRoot: AmperUserCacheRoot,
    private val taskOutputRoot: TaskOutputRoot,
    private val executeOnChangedInputs: ExecuteOnChangedInputs,
    override val taskName: TaskName,
    private val tempRoot: AmperProjectTempRoot,
): Task {
    init {
        require(platform.isLeaf)
        require(platform.topmostParentNoCommon == Platform.NATIVE)
    }

    override suspend fun run(dependenciesResult: List<org.jetbrains.amper.tasks.TaskResult>): TaskResult {
        val fragments = module.fragments.filter {
            it.platforms.contains(platform)
        }
        if (fragments.isEmpty()) {
            error("Zero fragments in module ${module.userReadableName} for platform $platform")
        }

        // TODO dependencies support
        // TODO kotlin version settings
        val kotlinVersion = KotlinCompilerUtil.AMPER_DEFAULT_KOTLIN_VERSION
        val kotlinNativeHome = KotlinCompilerUtil.downloadAndExtractKotlinNative(kotlinVersion, userCacheRoot)
            ?: error("kotlin native compiler is not available at this platform")

        val ext = if (org.jetbrains.amper.util.OS.isWindows) ".bat" else ""
        val konancExecutable = kotlinNativeHome.resolve("bin").resolve("konanc$ext")
        if (!konancExecutable.isExecutable()) {
            error("kotlin native home does not have konanc executable at $konancExecutable")
        }

        logger.info("native compile ${module.userReadableName} -- ${fragments.joinToString(" ") { it.name }}")

        // -g debug
        // -ea
        // -target
        // -kotlin-home ?

        // todo native resources are what exactly?
        // todo let's consider a program which imports all code from dependencies
        //  also an entry point
        val existingSourceRoots = fragments.map { it.src }.filter { it.exists() }
        if (existingSourceRoots.isEmpty()) {
            logger.warn("sources and resources are missing at ${fragments.joinToString(" ") { "'${it.src}'" }}'. Assuming no compilation step is required")
            return TaskResult(
                dependencies = dependenciesResult,
                artifact = null,
            )
        }

        // TODO this is JDK to run konanc, what are the requirements?
        val jdkHome = JdkDownloader.getJdkHome(userCacheRoot)
        val javaExecutable = JdkDownloader.getJavaExecutable(jdkHome)

        val configuration: Map<String, String> = mapOf(
            "konanc.jdk.version" to JdkDownloader.JBR_SDK_VERSION,
            "kotlin.version" to kotlinVersion,
            "task.output.root" to taskOutputRoot.path.pathString,
        )

        val inputs = existingSourceRoots
        val artifact = executeOnChangedInputs.execute(taskName.toString(), configuration, inputs) {
            cleanDirectory(taskOutputRoot.path)

            val artifact = taskOutputRoot.path.resolve(module.userReadableName + ".kexe")

            val args = listOf(
                "-g",
                "-ea",
                "-produce", "program",
                // TODO full module path including entire hierarchy
                "-module-name", module.userReadableName,
                "-Xshort-module-name=${module.userReadableName}",
                "-output", artifact.pathString,
                "-target", platform.name.lowercase(),
                "-Xmulti-platform",
            ) + existingSourceRoots.map { it.pathString }

            val environment = mapOf(
                "JAVACMD" to javaExecutable.pathString,
            )

            KotlinCompilerUtil.withKotlinCompilerArgFile(args, tempRoot) { argFile ->
                spanBuilder("konanc")
                    .setAttribute("amper-module", module.userReadableName)
                    .setAttribute(AttributeKey.stringArrayKey("args"), args)
                    .setAttribute("version", kotlinVersion)
                    .useWithScope { span ->
                        logger.info("Calling konanc ${ShellQuoting.quoteArgumentsPosixShellWay(args)}")
                        BuildPrimitives.runProcessAndAssertExitCode(
                            listOf(konancExecutable.pathString, "@$argFile"), kotlinNativeHome, span, environment)
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
        val artifact: Path?,
    ) : org.jetbrains.amper.tasks.TaskResult

    private val logger = LoggerFactory.getLogger(javaClass)
}
