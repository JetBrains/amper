/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.tasks.ios

import com.jetbrains.cidr.xcode.frameworks.buildSystem.BuildSettingNames
import kotlinx.serialization.json.Json
import org.jetbrains.amper.BuildPrimitives
import org.jetbrains.amper.cli.AmperBuildOutputRoot
import org.jetbrains.amper.cli.CliContext
import org.jetbrains.amper.cli.commands.tools.XCodeIntegrationCommand
import org.jetbrains.amper.cli.telemetry.setAmperModule
import org.jetbrains.amper.cli.userReadableError
import org.jetbrains.amper.core.telemetry.spanBuilder
import org.jetbrains.amper.engine.BuildTask
import org.jetbrains.amper.engine.TaskGraphExecutionContext
import org.jetbrains.amper.engine.requireSingleDependency
import org.jetbrains.amper.frontend.AmperModule
import org.jetbrains.amper.frontend.Platform
import org.jetbrains.amper.frontend.TaskName
import org.jetbrains.amper.frontend.isDescendantOf
import org.jetbrains.amper.processes.LoggingProcessOutputListener
import org.jetbrains.amper.tasks.TaskOutputRoot
import org.jetbrains.amper.tasks.TaskResult
import org.jetbrains.amper.telemetry.setListAttribute
import org.jetbrains.amper.telemetry.use
import org.jetbrains.amper.util.BuildType
import org.slf4j.LoggerFactory
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.absolutePathString
import kotlin.io.path.createDirectories
import kotlin.io.path.div
import kotlin.io.path.pathString
import kotlin.io.path.readText


class IosBuildTask(
    override val platform: Platform,
    override val module: AmperModule,
    override val buildType: BuildType,
    private val buildOutputRoot: AmperBuildOutputRoot,
    private val taskOutputPath: TaskOutputRoot,
    override val taskName: TaskName,
    override val isTest: Boolean,
) : BuildTask {
    init {
        require(platform.isDescendantOf(Platform.IOS)) { "Invalid iOS platform: $platform" }
    }

    override suspend fun run(dependenciesResult: List<TaskResult>, executionContext: TaskGraphExecutionContext): TaskResult {
        val projectInitialInfo = dependenciesResult.requireSingleDependency<ManageXCodeProjectTask.Result>()
        val xcodeSettings = projectInitialInfo.resolvedXcodeSettings[buildType] ?: run {
            // TODO: Assist user in creating this configuration back?
            userReadableError("Missing ${buildType.name} configuration in Xcode project.")
        }

        val workingDir = taskOutputPath.path.createDirectories()
        val derivedDataPath = workingDir / "derivedData"
        val objRootPath = workingDir / "tmp"
        val symRootPath = workingDir / "bin"

        val xcodebuildArgs = buildList {
            this += "xcrun"
            this += "xcodebuild"
            this += "-project"; this += projectInitialInfo.projectDir.pathString
            this += "-scheme"; this += projectInitialInfo.targetName
            this += "-configuration"; this += buildType.name
            this += "-arch"; this += platform.architecture
            this += "-derivedDataPath"; this += derivedDataPath.pathString
            this += "-sdk"; this += platform.sdk
            this += "${BuildSettingNames.OBJROOT}=${objRootPath.pathString}"
            this += "${BuildSettingNames.SYMROOT}=${symRootPath.pathString}"
            this += "AMPER_WRAPPER_PATH=${CliContext.wrapperScriptPath.absolutePathString()}"
            if (!platform.isIosSimulator && !xcodeSettings.hasTeamId && !xcodeSettings.isSigningDisabled) {
                logger.warn("`DEVELOPMENT_TEAM` build setting is not detected in the Xcode project. " +
                        "Adding `CODE_SIGNING_ALLOWED=NO` to disable signing. " +
                        "You can still sign the app manually later.")
                this += "CODE_SIGNING_ALLOWED=NO"
            }
            this += "build"
        }
        spanBuilder("xcodebuild")
            .setAmperModule(module)
            .setListAttribute("args", xcodebuildArgs)
            .use { span ->
                // TODO Maybe we dont need output here?
                val result = BuildPrimitives.runProcessAndGetOutput(
                    workingDir = workingDir,
                    command = xcodebuildArgs,
                    span = span,
                    environment = mapOf(
                        XCodeIntegrationCommand.AMPER_BUILD_OUTPUT_DIR_ENV to buildOutputRoot.path.pathString,
                        XCodeIntegrationCommand.AMPER_MODULE_NAME_ENV to module.userReadableName,
                    ),
                    outputListener = LoggingProcessOutputListener(logger),
                )
                if (result.exitCode != 0) {
                    userReadableError("xcodebuild invocation failed: \n${result.stderr}")
                }
            }

        val iosConventions = IosConventions(
            buildRootPath = buildOutputRoot.path,
            moduleName = module.userReadableName,
            buildType = buildType,
            platform = platform,
        )
        val outputDescription: IosConventions.BuildOutputDescription =
            Json.decodeFromString(iosConventions.getBuildOutputDescriptionFilePath().readText())
        return Result(
            bundleId = outputDescription.productBundleId,
            appPath = Path(outputDescription.appPath),
        )
    }

    class Result(
        val bundleId: String,
        val appPath: Path,
    ) : TaskResult

    private val logger = LoggerFactory.getLogger(javaClass)
}
