/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.tasks.ios

import kotlinx.coroutines.delay
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.jetbrains.amper.BuildPrimitives
import org.jetbrains.amper.cli.userReadableError
import org.jetbrains.amper.processes.LoggingProcessOutputListener
import org.jetbrains.amper.processes.ProcessOutputListener
import org.slf4j.LoggerFactory
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.pathString
import kotlin.time.Duration.Companion.milliseconds

data class Device(
    val deviceId: String,
    val runtimeId: String,
    val deviceTypeId: String,
    val isBooted: Boolean,
    val name: String,
)

const val XCRUN_EXECUTABLE = "/usr/bin/xcrun"

private const val SIM_RUNTIME_PREFIX_IOS = "com.apple.CoreSimulator.SimRuntime.iOS-"

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

suspend fun queryDevice(deviceId: String): Device? {
    return SimCtl.queryDevice(deviceId)
}

private fun Boolean.toInt() = if (this) 1 else 0

suspend fun bootAndWaitSimulator(
    device: Device,
    forceShowWindow: Boolean = false,
) {
    if (forceShowWindow) {
        // The `open` command works without any errors/warnings regardless of the simulator boot status.
        // It boots the simulator on demand and brings its window forward.
        BuildPrimitives.runProcessAndGetOutput(
            workingDir = Path("."),
            command = listOf("open", "-a", "Simulator"),
            outputListener = LoggingProcessOutputListener(logger),
        )
    }

    if (device.isBooted) {
        return
    }

    BuildPrimitives.runProcessAndGetOutput(
        workingDir = Path("."),
        command = listOf("xcrun", "simctl", "boot", device.deviceId),
        outputListener = LoggingProcessOutputListener(logger),
    )
    repeat(20) {
        val device = SimCtl.queryDevice(device.deviceId) ?: userReadableError(
            "Simulator device `${device.deviceId}` disappeared unexpectedly while waiting to be booted."
        )
        if (device.isBooted) {
            return  // Success
        }
        delay(500.milliseconds)
    }
    userReadableError("Simulator boot timeout for `${device.deviceId}`.")
}

private suspend fun xcrun(
    vararg args: String,
    listener: ProcessOutputListener = LoggingProcessOutputListener(logger),
) = BuildPrimitives.runProcessAndGetOutput(
    workingDir = Path("."),
    command = listOf(XCRUN_EXECUTABLE) + args,
    outputListener = listener,
).also {
    if (it.exitCode != 0) {
        userReadableError("xcrun `${args.contentToString()}` failed with exit code ${it.exitCode}")
    }
}.stdout

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

        return SimCtlOutputFormat.decodeFromString<SimCtlListOutput>(simcltListOut).devices
            .filter { (runtimeId, _) -> runtimeId.startsWith(SIM_RUNTIME_PREFIX_IOS) }
            .flatMap { (runtimeId, devices) ->
                devices
                    .filter { it.isAvailable }
                    .map { it.toDevice(runtimeId) }
            }
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

suspend fun installAppOnPhysicalDevice(
    deviceId: String,
    appPath: Path,
) = xcrun("devicectl", "device", "install", "app", "--device", deviceId, appPath.pathString)

suspend fun launchAppOnPhysicalDevice(
    deviceId: String,
    bundleId: String,
) = xcrun("devicectl", "device", "process", "launch", "--device", deviceId, bundleId)

private val logger = LoggerFactory.getLogger("org.jetbrains.amper.tasks.ios")