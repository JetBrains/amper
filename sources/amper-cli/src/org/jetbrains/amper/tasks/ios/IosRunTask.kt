/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.tasks.ios

import org.jetbrains.amper.BuildPrimitives
import org.jetbrains.amper.cli.userReadableError
import org.jetbrains.amper.engine.RunTask
import org.jetbrains.amper.engine.TaskGraphExecutionContext
import org.jetbrains.amper.engine.requireSingleDependency
import org.jetbrains.amper.frontend.AmperModule
import org.jetbrains.amper.frontend.Platform
import org.jetbrains.amper.frontend.TaskName
import org.jetbrains.amper.processes.ProcessOutputListener
import org.jetbrains.amper.tasks.EmptyTaskResult
import org.jetbrains.amper.tasks.MobileRunSettings
import org.jetbrains.amper.tasks.TaskOutputRoot
import org.jetbrains.amper.tasks.TaskResult
import org.jetbrains.amper.util.BuildType
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.absolutePathString
import kotlin.io.path.createDirectories

class IosRunTask(
    override val taskName: TaskName,
    override val platform: Platform,
    override val buildType: BuildType,
    override val module: AmperModule,
    private val runSettings: MobileRunSettings,
    private val taskOutputPath: TaskOutputRoot,
) : RunTask {
    override suspend fun run(dependenciesResult: List<TaskResult>, executionContext: TaskGraphExecutionContext): TaskResult {
        taskOutputPath.path.createDirectories()
        val builtApp = dependenciesResult.requireSingleDependency<IosBuildTask.Result>()
        if (platform.isIosSimulator) {
            val simulatorDevice = selectSimulatorDevice()
            bootAndWaitSimulator(simulatorDevice, forceShowWindow = true)
            installAppOnDevice(simulatorDevice.deviceId, builtApp.appPath)
            launchAppOnDevice(simulatorDevice.deviceId, builtApp.bundleId)
        } else {
            // Physical device
            if (!checkAppIsSigned(builtApp.appPath)) {
                userReadableError("Running an unsigned app on a physical device (${platform.pretty}) is not possible. " +
                        "Please select a development team in the Xcode project editor (Signing & Capabilities) " +
                        "or use a simulator platform instead.")
            }
            val deviceId = runSettings.deviceId
                ?: userReadableError("To run on a physical iOS device, the -d/--device-id argument must be specified.\n" +
                        "Use `xcrun devicectl list devices` command to see what devices are available.")
            installAppOnPhysicalDevice(deviceId, builtApp.appPath)
            launchAppOnPhysicalDevice(deviceId, builtApp.bundleId)
        }
        return EmptyTaskResult
    }

    private suspend fun selectSimulatorDevice(): Device {
        val id = runSettings.deviceId
        return if (id != null) {
            queryDevice(id) ?: userReadableError("Unable to find the iOS simulator with the specified id: `$id`")
        } else {
            pickBestDevice() ?: userReadableError("Unable to detect any usable iOS simulator, check your environment")
        }
    }

    private suspend fun checkAppIsSigned(appPath: Path): Boolean {
        return BuildPrimitives.runProcessAndGetOutput(
            workingDir = Path("."),
            command = listOf("codesign", "-v", appPath.absolutePathString()),
            outputListener = ProcessOutputListener.NOOP,
        ).exitCode == 0
    }
}
