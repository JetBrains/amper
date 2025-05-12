/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.tasks.ios

import com.jetbrains.cidr.xcode.frameworks.buildSystem.BuildSettingNames
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import org.jetbrains.amper.BuildPrimitives
import org.jetbrains.amper.cli.CliContext
import org.jetbrains.amper.cli.telemetry.setAmperModule
import org.jetbrains.amper.cli.userReadableError
import org.jetbrains.amper.core.AmperUserCacheRoot
import org.jetbrains.amper.core.downloader.Downloader
import org.jetbrains.amper.core.extract.extractFileToCacheLocation
import org.jetbrains.amper.core.system.Arch
import org.jetbrains.amper.core.system.DefaultSystemInfo
import org.jetbrains.amper.core.telemetry.spanBuilder
import org.jetbrains.amper.engine.BuildTask
import org.jetbrains.amper.engine.TaskGraphExecutionContext
import org.jetbrains.amper.engine.requireSingleDependency
import org.jetbrains.amper.frontend.AmperModule
import org.jetbrains.amper.frontend.Platform
import org.jetbrains.amper.frontend.TaskName
import org.jetbrains.amper.frontend.isDescendantOf
import org.jetbrains.amper.processes.LoggingProcessOutputListener
import org.jetbrains.amper.processes.ProcessInput
import org.jetbrains.amper.processes.ProcessOutputListener
import org.jetbrains.amper.processes.runProcess
import org.jetbrains.amper.tasks.TaskOutputRoot
import org.jetbrains.amper.tasks.TaskResult
import org.jetbrains.amper.telemetry.setListAttribute
import org.jetbrains.amper.telemetry.use
import org.jetbrains.amper.util.BuildType
import org.slf4j.LoggerFactory
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermission
import kotlin.io.path.absolutePathString
import kotlin.io.path.bufferedWriter
import kotlin.io.path.createDirectories
import kotlin.io.path.div
import kotlin.io.path.getPosixFilePermissions
import kotlin.io.path.isExecutable
import kotlin.io.path.pathString
import kotlin.io.path.setPosixFilePermissions


class IosBuildTask(
    override val platform: Platform,
    override val module: AmperModule,
    override val buildType: BuildType,
    private val taskOutputPath: TaskOutputRoot,
    override val taskName: TaskName,
    private val userCacheRoot: AmperUserCacheRoot,
) : BuildTask {
    init {
        require(platform.isDescendantOf(Platform.IOS)) { "Invalid iOS platform: $platform" }
    }

    override val isTest: Boolean
        get() = false

    override suspend fun run(dependenciesResult: List<TaskResult>, executionContext: TaskGraphExecutionContext): TaskResult {
        val projectInitialInfo = dependenciesResult.requireSingleDependency<ManageXCodeProjectTask.Result>()
        val prebuildResult = dependenciesResult.requireSingleDependency<IosPreBuildTask.Result>()
        val xcodeSettings = projectInitialInfo.getResolvedXcodeSettings(buildType)

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

        coroutineScope {
            val executable = prepareLogParsingUtility()
            val pipe = ProcessInput.Pipe()

            logger.info("Using xcbeautify ($XCBEAUTIFY_VERSION) to parse xcodebuild output.")

            // Need to launch log parser in parallel
            val parserProcessJob = launch {
                runProcess(
                    workingDir = workingDir,
                    command = listOf(
                        executable.pathString,
                        "--disable-logging", // disable big version banner - we do it ourselves
                    ),
                    outputListener = LoggingProcessOutputListener(logger),
                    input = pipe,
                )
            }

            val fullXcodebuildLog = taskOutputPath.path / "xcodebuild.log"
            val outputListener = pipe.pipeInListener + FileLoggingProcessOutputListener(fullXcodebuildLog)
            spanBuilder("xcodebuild")
                .setAmperModule(module)
                .setListAttribute("args", xcodebuildArgs)
                .use { span ->
                    val result = BuildPrimitives.runProcessAndGetOutput(
                        workingDir = workingDir,
                        command = xcodebuildArgs,
                        span = span,
                        environment = mapOf(
                            IosPreBuildTask.Result.ENV_JSON_NAME to Json.encodeToString(prebuildResult),
                        ),
                        redirectErrorStream = true,
                        outputListener = outputListener,
                    )

                    // Ensure the parser is done to avoid putting the final log entries into the middle of the log
                    parserProcessJob.join()

                    logger.info("Full xcodebuild log can be found at file://${fullXcodebuildLog.absolutePathString()}")
                    if (result.exitCode != 0) {
                        userReadableError("xcodebuild invocation failed, check the log above.")
                    }
                }
        }

        return Result(
            bundleId = xcodeSettings.bundleId,
            appPath = symRootPath / "${buildType.name}-${platform.sdk}" / "${xcodeSettings.productName}.app",
        )
    }

    private suspend fun prepareLogParsingUtility(): Path {
        val archString = when(DefaultSystemInfo.detect().arch) {
            Arch.X64 -> "x86_64"
            Arch.Arm64 -> "arm64"
        }
        val version = XCBEAUTIFY_VERSION
        val archive = Downloader.downloadFileToCacheLocation(
            url = "https://github.com/cpisciotta/xcbeautify/releases/download/$version/xcbeautify-$version-$archString-apple-macosx.zip",
            userCacheRoot = userCacheRoot,
        )
        val executable = extractFileToCacheLocation(archiveFile = archive, amperUserCacheRoot = userCacheRoot)
            .resolve("xcbeautify")
        if (!executable.isExecutable()) {
            val permissions = executable.getPosixFilePermissions()
            executable.setPosixFilePermissions(permissions + PosixFilePermission.OWNER_EXECUTE)
        }
        return executable
    }

    private class FileLoggingProcessOutputListener(
        logFile: Path,
    ) : ProcessOutputListener {
        private val stream = logFile.bufferedWriter()
        override fun onStdoutLine(line: String, pid: Long) = stream.write("out: $line\n")
        override fun onStderrLine(line: String, pid: Long) = stream.write("err: $line\n")
        override fun onProcessTerminated(exitCode: Int, pid: Long) = stream.close()
    }

    class Result(
        val bundleId: String,
        val appPath: Path,
    ) : TaskResult

    private val logger = LoggerFactory.getLogger(javaClass)
}

const val XCBEAUTIFY_VERSION = "2.28.0"