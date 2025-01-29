/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package iosUtils

import kotlinx.coroutines.delay
import org.jetbrains.amper.processes.ProcessLeak
import org.jetbrains.amper.processes.runProcessAndCaptureOutput
import org.jetbrains.amper.processes.startLongLivedProcess
import kotlin.io.path.Path
import kotlin.io.path.exists
import kotlin.io.path.pathString
import kotlin.time.Duration.Companion.seconds

/**
 * Manages the iOS emulator lifecycle and provides helper methods
 */
object SimulatorManager {
    private val simulatorPath = Path("/Applications/Xcode.app/Contents/Developer/Applications/Simulator.app")
    /**
     * Launches the iOS Simulator with the specified device.
     */
    @ProcessLeak
    suspend fun launchSimulator(deviceName: String = "iPhone 16") {
        if (!simulatorPath.exists()) {
            error("Simulator app not found at $simulatorPath")
        }

        val deviceId =
            getDeviceUUIDForLatestIOS(deviceName) ?: error("Device $deviceName not found with latest iOS version.")
        println("Resolved device ID for $deviceName: $deviceId")

        runProcessAndCaptureOutput(command = listOf("xcrun", "simctl", "boot", deviceId))
        startLongLivedProcess(command = listOf("open", simulatorPath.pathString))

        repeat(3) { attempt ->
            delay(5.seconds)

            if (isSimulatorRunning(deviceId)) {
                println("Simulator is now running.")
                return
            }

            println("Attempt ${attempt + 1}: Simulator not yet initialized, retrying...")
        }

        error("Simulator $deviceName failed to start or initialize after multiple attempts.")
    }

    /**
     * Retrieves the UUID of the specified device with the latest available iOS version.
     */
    private suspend fun getDeviceUUIDForLatestIOS(deviceName: String): String? {
        /**
         * The iOS Simulator that is launched must be the latest version available in the system.
         * This is essential because Amper builds the app file targeting the highest available
         * iOS version by default.
         */
        val process = runProcessAndCaptureOutput(
            command = listOf("xcrun", "simctl", "list", "devices")
        )

        if (process.exitCode != 0) {
            error("Failed to retrieve devices list: ${process.stderr}")
        }

        println("Devices list output:\n${process.stdout}")

        val latestIOSVersion = process.stdout.lines()
            .filter { it.startsWith("-- iOS") }
            .mapNotNull { line ->
                line.substringAfter("-- iOS ").substringBefore(" --").toDoubleOrNull()
            }
            .maxOrNull()
            ?.toString()

        println("Latest iOS version found: $latestIOSVersion")

        if (latestIOSVersion == null) {
            return null
        }

        val devicesSection = process.stdout.lines()
            .dropWhile { !it.contains("iOS $latestIOSVersion") } // Start at the latest iOS version
            .drop(1) // Skip the version header itself
            .takeWhile { !it.startsWith("--") } // Take until the next section starts

        println("Devices section for iOS $latestIOSVersion:")
        devicesSection.forEach { println(it) }

        val deviceUUID = devicesSection
            .firstOrNull { line -> line.contains(deviceName) }
            ?.substringAfter("(")
            ?.substringBefore(")")

        println("Resolved device UUID for $deviceName with iOS $latestIOSVersion: $deviceUUID")

        return deviceUUID
    }

    /**
     * Returns true if the specified simulator device is running.
     */
    private suspend fun isSimulatorRunning(deviceId: String): Boolean {
        val process = runProcessAndCaptureOutput(
            command = listOf("xcrun", "simctl", "list", "devices", "booted")
        )

        if (process.exitCode != 0) {
            error("Failed to check simulator status: ${process.stderr}")
        }

        return process.stdout.lines().any { line ->
            line.contains(deviceId) && line.contains("(Booted)")
        }
    }
}
