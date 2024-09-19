/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.tasks.ios

import org.jetbrains.amper.engine.Task
import org.jetbrains.amper.engine.requireSingleDependency
import org.jetbrains.amper.frontend.TaskName
import org.jetbrains.amper.tasks.BaseTaskResult
import org.jetbrains.amper.tasks.TaskOutputRoot
import org.jetbrains.amper.tasks.TaskResult
import kotlin.io.path.createDirectories

class RunAppleTask(
    override val taskName: TaskName,
    private val taskOutputPath: TaskOutputRoot,
) : Task {
    override suspend fun run(dependenciesResult: List<TaskResult>): TaskResult {
        taskOutputPath.path.createDirectories()
        val builtApp = dependenciesResult.requireSingleDependency<BuildAppleTask.Result>()
        val chosenDevice = queryDevices().firstOrNull() ?: error("No available device")
        bootAndWaitSimulator(chosenDevice.deviceId, headless = false)
        installAppOnDevice(chosenDevice.deviceId, builtApp.appPath)
        launchAppOnDevice(chosenDevice.deviceId, builtApp.bundleId)
        return BaseTaskResult()
    }
}
