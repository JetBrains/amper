/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.tasks.ios

import org.jetbrains.amper.engine.requireSingleDependency
import org.jetbrains.amper.frontend.AmperModule
import org.jetbrains.amper.frontend.Platform
import org.jetbrains.amper.frontend.TaskName
import org.jetbrains.amper.tasks.BaseTaskResult
import org.jetbrains.amper.tasks.RunTask
import org.jetbrains.amper.tasks.TaskOutputRoot
import org.jetbrains.amper.tasks.TaskResult
import org.jetbrains.amper.util.BuildType
import kotlin.io.path.createDirectories

class IosRunTask(
    override val taskName: TaskName,
    override val platform: Platform,
    override val buildType: BuildType,
    override val module: AmperModule,
    private val taskOutputPath: TaskOutputRoot,
) : RunTask {
    init {
        require(platform.isIosSimulator) {
            "`$platform` is not a simulator platform"
        }
    }

    override suspend fun run(dependenciesResult: List<TaskResult>): TaskResult {
        taskOutputPath.path.createDirectories()
        val builtApp = dependenciesResult.requireSingleDependency<IosBuildTask.Result>()
        val chosenDevice = pickBestDevice() ?: error("No available device")
        bootAndWaitSimulator(chosenDevice.deviceId, forceShowWindow = true)
        installAppOnDevice(chosenDevice.deviceId, builtApp.appPath)
        launchAppOnDevice(chosenDevice.deviceId, builtApp.bundleId)
        return BaseTaskResult()
    }
}
