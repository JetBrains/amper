/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.tasks.ios

import com.jetbrains.cidr.xcode.frameworks.buildSystem.BuildSettingNames
import org.jetbrains.amper.BuildPrimitives
import org.jetbrains.amper.engine.Task
import org.jetbrains.amper.engine.TaskName
import org.jetbrains.amper.frontend.Platform
import org.jetbrains.amper.frontend.PotatoModule
import org.jetbrains.amper.frontend.forClosure
import org.jetbrains.amper.intellij.MockProjectInitializer
import org.jetbrains.amper.tasks.NativeCompileTask
import org.jetbrains.amper.tasks.TaskOutputRoot
import org.jetbrains.amper.tasks.TaskResult
import org.jetbrains.amper.util.BuildType
import org.jetbrains.amper.util.ExecuteOnChangedInputs
import org.jetbrains.amper.util.ShellQuoting
import org.slf4j.LoggerFactory
import java.nio.file.Path
import kotlin.io.path.pathString


class BuildAppleTask(
    private val targetPlatform: Platform,
    private val module: PotatoModule,
    private val buildType: BuildType,
    private val executeOnChangedInputs: ExecuteOnChangedInputs,
    private val taskOutputPath: TaskOutputRoot,
    override val taskName: TaskName,
) : Task {
    override suspend fun run(dependenciesResult: List<TaskResult>): TaskResult {
        val nativeCompileTasksResults = dependenciesResult
            .filterIsInstance<NativeCompileTask.TaskResult>()
            .map { it.artifact }

        val project = MockProjectInitializer.mockProject
        val leafAppleFragment = module.leafFragments.first { it.platform == targetPlatform }
        val targetName = "${targetPlatform.pretty}App"
        val productName = module.userReadableName

        // Define required apple sources.
        val currentPlatformFamily = targetPlatform.parent?.let { it.leaves + it } ?: emptySet()
        val appleSources = buildSet {
            leafAppleFragment.forClosure {
                if (currentPlatformFamily.containsAll(it.platforms)) add(it.src.toFile().normalize())
            }
        }

        // TODO Add Assertion for apple platform.

        return with(FileConventions(project, module, taskOutputPath.path.toFile())) {
            val appPath = symRoot
                .resolve("${buildType.variantName}-${targetPlatform.platform}")
                .resolve("${productName}.app")

            // TODO Add all other ios settings.
            val config = mapOf(
                "target.platform" to targetPlatform.pretty,
                "task.output.root" to taskOutputPath.path.pathString,
                "build.type" to buildType.value,
            )

            executeOnChangedInputs.execute(
                "build-${targetPlatform.pretty}-app",
                config,
                appleSources.map { it.toPath() } + nativeCompileTasksResults,
            ) {
                doGenerateBuildableXcodeproj(
                    module,
                    leafAppleFragment,
                    targetName,
                    productName,
                    buildType,
                    appleSources,
                    nativeCompileTasksResults.map { it.toFile() },
                )

                val command = listOf(
                    "xcrun",
                    "xcodebuild",
                    "-project", projectDir.path,
                    "-scheme", targetName,
                    "-configuration", "Debug",
                    "${BuildSettingNames.OBJROOT}=$objRootPathString",
                    "${BuildSettingNames.SYMROOT}=$symRootPathString",
                    "-arch", targetPlatform.architecture,
                    "-derivedDataPath", derivedDataPathString,
                    "-sdk", targetPlatform.platform,
                    "build",
                )

                logger.info("Calling xcode build: ${ShellQuoting.quoteArgumentsPosixShellWay(command)}")
                BuildPrimitives.runProcessAndGetOutput(
                    command,
                    baseDir.toPath(),
                )

                return@execute ExecuteOnChangedInputs.ExecutionResult(listOf(appPath.toPath()))
            }

            Result(appPath.toPath())
        }
    }

    class Result(
        val appPath: Path,
    ) : TaskResult {
        override val dependencies = emptyList<TaskResult>()
    }

    private val logger = LoggerFactory.getLogger(javaClass)

}
