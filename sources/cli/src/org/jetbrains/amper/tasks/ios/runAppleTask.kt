/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.tasks.ios

import kotlinx.coroutines.delay
import org.jetbrains.amper.BuildPrimitives
import org.jetbrains.amper.engine.Task
import org.jetbrains.amper.engine.TaskName
import org.jetbrains.amper.tasks.TaskOutputRoot
import org.jetbrains.amper.tasks.TaskResult
import org.slf4j.LoggerFactory
import java.util.*
import kotlin.io.path.createDirectories
import kotlin.io.path.pathString

class RunAppleTask(
    override val taskName: TaskName,
    private val taskOutputPath: TaskOutputRoot,
) : Task {
    companion object {
        const val AWAIT_ATTEMPTS = 10
        const val AWAIT_TIME: Long = 1000
    }

    override suspend fun run(dependenciesResult: List<TaskResult>): TaskResult {
        taskOutputPath.path.createDirectories()

        val builtApp = dependenciesResult
            .filterIsInstance<BuildAppleTask.Result>()
            .firstOrNull() ?: error("Expected to have \"BuildAppleTask\" as a dependency")

        val chosenDevice = queryDevices().firstOrNull()
            ?: error("No available device")

        BuildPrimitives.runProcessAndGetOutput(
            taskOutputPath.path,
            "open", "-a", "Simulator", "--args", "-CurrentDeviceUDID", chosenDevice.deviceId,
            logCall = true,
        )

        // Wait for booting.
        for (i in 0..AWAIT_ATTEMPTS) {
            val deviceStatus = queryDevices(chosenDevice.deviceId)
                .firstOrNull()
                ?.apply { assert(deviceId == chosenDevice.deviceId) }
                ?.status
                ?: error("Launched device not available: ${chosenDevice.deviceId}")
            if (deviceStatus == "booted") break
            else if (i == AWAIT_ATTEMPTS) error("Max boot await attempts exceeded for device: ${chosenDevice.deviceId}")
            else delay(AWAIT_TIME)
        }

        BuildPrimitives.runProcessAndGetOutput(
            taskOutputPath.path,
            "xcrun", "simctl", "install", chosenDevice.deviceId, builtApp.appPath.pathString,
            logCall = true,
        )

        BuildPrimitives.runProcessAndGetOutput(
            taskOutputPath.path,
            "xcrun", "simctl", "launch", chosenDevice.deviceId, builtApp.bundleId,
            logCall = true,
        )

        return Result()
    }

    data class Device(val deviceId: String, val status: String)

    private suspend fun queryDevices(filter: String = "available"): List<Device> {
        val simcltListOut = BuildPrimitives.runProcessAndGetOutput(
            taskOutputPath.path,
            "xcrun", "simctl", "list", "-v", "devices", filter,
            logCall = filter == "available",
            hideOutput = true,
        ).stdout.lines()

        // TODO Rework for json parsing later.
        val firstDevice = simcltListOut
            // Something like "-- iOS 16.4 (20E247) ..."
            .indexOfFirst { it.startsWith("-- iOS") }
            .takeIf { it != -1 }
            ?: error("No devices available")

        val devices = simcltListOut.drop(firstDevice + 1)
            .takeWhile { !it.startsWith("--") && it.isNotBlank() }

        val deviceRegex = ".+ \\((.+)\\) \\((.+)\\)".toRegex()

        return devices
            .map { it.trim() }
            .mapNotNull { deviceRegex.find(it) }
            .map { Device(it.groupValues[1], it.groupValues[2].lowercase(Locale.getDefault())) }
    }

    class Result : TaskResult {
        override val dependencies = emptyList<TaskResult>()
    }

    private val logger = LoggerFactory.getLogger(javaClass)
}