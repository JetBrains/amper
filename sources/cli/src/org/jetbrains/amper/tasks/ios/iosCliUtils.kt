/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.tasks.ios

import kotlinx.coroutines.delay
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.jetbrains.amper.BuildPrimitives
import org.jetbrains.amper.core.toInt
import org.jetbrains.amper.processes.LoggingProcessOutputListener
import org.jetbrains.amper.processes.ProcessOutputListener
import org.slf4j.LoggerFactory
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.pathString

data class Device(
    val deviceId: String,
    val runtimeId: String,
    val deviceTypeId: String,
    val isBooted: Boolean,
    val name: String,
)

private const val AWAIT_ATTEMPTS = 10
private const val AWAIT_TIME: Long = 1000
const val XCRUN_EXECUTABLE = "/usr/bin/xcrun"

suspend fun installAppOnDevice(deviceId: String, appPath: Path) =
    xcrun("simctl", "install", deviceId, appPath.pathString)

suspend fun launchAppOnDevice(deviceId: String, bundleId: String) =
    xcrun("simctl", "launch", deviceId, bundleId)

suspend fun shutdownDevice(deviceId: String) =
    xcrun("simctl", "shutdown", deviceId)

suspend fun pickBestDevice(): Device? {
    val devices = SimCtl.queryAvailableDevices().sortedByDescending { it.runtimeId }
    val latestRuntime = devices.firstOrNull()?.runtimeId ?: return null
    // Naive ranking algorithm
    return devices.takeWhile { it.runtimeId == latestRuntime }.maxBy {
        10_000 * ("iPhone" in it.deviceTypeId).toInt() +
                1_000 * ("Pro" in it.deviceTypeId).toInt() +
                100 * ("Max" in it.deviceTypeId).toInt()
    }
}

suspend fun bootAndWaitSimulator(
    deviceId: String,
    forceShowWindow: Boolean = false,
) {
    var bootCommandIssued = false
    suspend fun ensureBootCommandIssued() {
        if (bootCommandIssued) return
        BuildPrimitives.runProcessAndGetOutput(
            workingDir = Path("."),
            command = if (forceShowWindow)
                listOf("open", "-a", "Simulator", "--args", "-CurrentDeviceUDID", deviceId)
            else
                listOf("xcrun", "simctl", "boot", deviceId),
            outputListener = LoggingProcessOutputListener(logger),
        )
        bootCommandIssued = true
    }

    if (forceShowWindow) {
        // The `open` command works without any errors/warnings regardless of the simulator boot status.
        // It boots the simulator on demand and brings its window forward.
        ensureBootCommandIssued()
    }

    // Wait for booting.
    for (i in 0..AWAIT_ATTEMPTS) {
        val device = SimCtl.queryDevice(deviceId) ?: error("Device is not available: $deviceId")
        if (device.isBooted) break

        ensureBootCommandIssued()

        if (i >= AWAIT_ATTEMPTS) error("Max boot await attempts exceeded for device: $deviceId")
        else delay(AWAIT_TIME)
    }
}

private suspend fun xcrun(
    vararg args: String,
    listener: ProcessOutputListener = LoggingProcessOutputListener(logger),
) = BuildPrimitives.runProcessAndGetOutput(
    workingDir = Path("."),
    command = listOf(XCRUN_EXECUTABLE) + args,
    outputListener = listener,
).stdout

private object SimCtl {
    private val SimCtlOutputFormat = Json {
        ignoreUnknownKeys = true
    }

    @Serializable
    private data class DeviceData(
        val udid: String,
        val isAvailable: Boolean,

        val deviceTypeIdentifier: String,
        val state: String,
        val name: String,
    )

    @Serializable
    private data class SimCtlListOutput(
        val devices: Map<String, List<DeviceData>>,
    )

    suspend fun queryDevice(deviceId: String): Device? {
        val simcltListOut = xcrun(
            "simctl", "list", "-v", "devices", deviceId, "--json",
            listener = ProcessOutputListener.NOOP,
        )

        return SimCtlOutputFormat.decodeFromString<SimCtlListOutput>(simcltListOut)
            .devices.mapNotNull { it.value.singleOrNull()?.toDevice(runtimeId = it.key) }.singleOrNull()
    }

    suspend fun queryAvailableDevices(): List<Device> {
        val simcltListOut = xcrun(
            "simctl", "list", "-v", "devices", "--json",
            listener = ProcessOutputListener.NOOP,
        )

        return SimCtlOutputFormat.decodeFromString<SimCtlListOutput>(simcltListOut)
            .devices.flatMap { (runtimeId, devices) -> devices.map { it.toDevice(runtimeId) } }
    }

    private fun DeviceData.toDevice(
        runtimeId: String,
    ) = Device(
        deviceId = udid,
        deviceTypeId = deviceTypeIdentifier,
        runtimeId = runtimeId,
        isBooted = state.lowercase() == "booted",
        name = name,
    )
}

private val logger = LoggerFactory.getLogger("org.jetbrains.amper.tasks.ios")