/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.tasks.ios

import org.jetbrains.amper.cli.userReadableError
import org.jetbrains.amper.engine.RunTask
import org.jetbrains.amper.engine.TaskGraphExecutionContext
import org.jetbrains.amper.engine.requireSingleDependency
import org.jetbrains.amper.frontend.AmperModule
import org.jetbrains.amper.frontend.Platform
import org.jetbrains.amper.frontend.TaskName
import org.jetbrains.amper.tasks.BaseTaskResult
import org.jetbrains.amper.tasks.CommonRunSettings
import org.jetbrains.amper.tasks.TaskOutputRoot
import org.jetbrains.amper.tasks.TaskResult
import org.jetbrains.amper.util.BuildType
import kotlin.io.path.createDirectories

class IosRunTask(
    override val taskName: TaskName,
    override val platform: Platform,
    override val buildType: BuildType,
    override val module: AmperModule,
    private val commonRunSettings: CommonRunSettings,
    private val taskOutputPath: TaskOutputRoot,
) : RunTask {
    override suspend fun run(dependenciesResult: List<TaskResult>, executionContext: TaskGraphExecutionContext): TaskResult {
        taskOutputPath.path.createDirectories()
        val builtApp = dependenciesResult.requireSingleDependency<IosBuildTask.Result>()
        if (platform.isIosSimulator) {
            val deviceId = commonRunSettings.deviceId ?: pickBestDevice()?.deviceId ?: error("No available device")
            bootAndWaitSimulator(deviceId, forceShowWindow = true)
            installAppOnDevice(deviceId, builtApp.appPath)
            launchAppOnDevice(deviceId, builtApp.bundleId)
        } else {
            // Physical device
            val deviceId = commonRunSettings.deviceId
                ?: userReadableError("To run on a physical iOS device, the -d/--device-id argument must be specified.\n" +
                        "Use `xcrun devicectl list devices` command to see what devices are available.")
            installAppOnPhysicalDevice(deviceId, builtApp.appPath)
            launchAppOnPhysicalDevice(deviceId, builtApp.bundleId)
        }
        return BaseTaskResult()
    }
}
