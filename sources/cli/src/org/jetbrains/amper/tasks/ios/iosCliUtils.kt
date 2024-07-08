/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.tasks.ios

import kotlinx.coroutines.delay
import org.jetbrains.amper.BuildPrimitives
import org.jetbrains.amper.processes.LoggingProcessOutputListener
import org.jetbrains.amper.processes.ProcessOutputListener
import org.slf4j.LoggerFactory
import java.nio.file.Path
import java.util.*
import kotlin.io.path.Path
import kotlin.io.path.pathString

data class Device(val deviceId: String, val status: String)

private const val AVAILABLE_DEVICES_FILTER = "available"
const val AWAIT_ATTEMPTS = 10
const val AWAIT_TIME: Long = 1000

suspend fun installAppOnDevice(deviceId: String, appPath: Path) =
    xcrun("simctl", "install", deviceId, appPath.pathString)

suspend fun launchAppOnDevice(deviceId: String, bundleId: String) =
    xcrun("simctl", "launch", deviceId, bundleId)

suspend fun queryDevices(
    filter: String = AVAILABLE_DEVICES_FILTER
): List<Device> {
    val simcltListOut = xcrun(
        "simctl", "list", "-v", "devices", filter,
        logCall = filter == AVAILABLE_DEVICES_FILTER,
        listener = ProcessOutputListener.NOOP,
    )

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

suspend fun bootAndWaitSimulator(deviceId: String) {
    BuildPrimitives.runProcessAndGetOutput(
        Path("."),
        "open", "-a", "Simulator", "--args", "-CurrentDeviceUDID", deviceId,
        logCall = true,
        outputListener = LoggingProcessOutputListener(logger),
    )

    // Wait for booting.
    for (i in 0..AWAIT_ATTEMPTS) {
        val deviceStatus = queryDevices(deviceId)
            .firstOrNull()
            ?.apply { assert(deviceId == deviceId) }
            ?.status
            ?: error("Launched device not available: $deviceId")
        if (deviceStatus == "booted") break
        else if (i >= AWAIT_ATTEMPTS) error("Max boot await attempts exceeded for device: $deviceId")
        else delay(AWAIT_TIME)
    }
}

private suspend fun xcrun(
    vararg args: String,
    logCall: Boolean = false,
    listener: ProcessOutputListener = LoggingProcessOutputListener(logger),
) = BuildPrimitives.runProcessAndGetOutput(
    Path("."),
    "/usr/bin/xcrun", *args,
    logCall = logCall,
    outputListener = listener,
).stdout.lines()

private val logger = LoggerFactory.getLogger("org.jetbrains.amper.tasks.ios")