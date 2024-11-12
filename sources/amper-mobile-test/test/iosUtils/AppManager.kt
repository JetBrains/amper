/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package iosUtils

import TestBase
import org.jetbrains.amper.processes.runProcessAndCaptureOutput
import java.nio.file.Path
import kotlin.io.path.absolutePathString
import kotlin.io.path.div
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name

/**
 * Manages iOS-app installation and execution on the iOS Simulator.
 * This class provides methods to install target apps on a simulator,
 * run apps via xcrun and helps ensure that the application is actually running.
 */
object AppManager {

    class AppNotFoundException(message: String) : Exception(message)

    /**
     * Starts the test process for the [projectName] iOS app by locating and installing
     * the app file on the simulator, then launching it with the specified [bundleIdentifier].
     * Configures behavior based on [multiplatform] and [standalone] settings.
     *
     * @throws AppNotFoundException if the app file is not found.
     */
    suspend fun launchTest(
        projectName: String,
        bundleIdentifier: String,
        multiplatform: Boolean,
        standalone: Boolean
    ) {
        val projectDir = TestBase().tempProjectsDir / projectName
        require(projectDir.exists() && projectDir.isDirectory()) {
            "Invalid project directory: $projectDir"
        }

        println("Processing project in directory: ${projectDir.name}")

        val appFile = findAppFile(projectDir, projectName, multiplatform, standalone)
        installAndVerifyAppLaunch(appFile, bundleIdentifier)
    }

    /**
     * Finds the app file for [projectName] in the specified [baseDir] by determining the
     * appropriate directory path based on [multiplatform] and [standalone] configurations.
     *
     */
    private fun findAppFile(
        baseDir: Path,
        projectName: String,
        multiplatform: Boolean,
        standalone: Boolean
    ): Path {
        val appDirectory = when {
            multiplatform && standalone ->
                baseDir / "build/tasks/_ios-app_buildIosAppIosSimulatorArm64/bin/Debug-iphonesimulator"

            multiplatform && !standalone ->
                baseDir / "ios-app/build/bin/ios-app/Debug-iphonesimulator"

            !multiplatform && standalone ->
                baseDir / "build/tasks/_${projectName}_buildIosAppIosSimulatorArm64/bin/Debug-iphonesimulator"

            else -> // !multiplatform && !standalone
                baseDir / "build/bin/$projectName/Debug-iphonesimulator"
        }

        return appDirectory.listDirectoryEntries("*.app").firstOrNull()
            ?: throw AppNotFoundException("No app files found in $appDirectory")
    }

    /**
     * Installs an iOS app on the booted simulator, launches it, verifies its successful start,
     * and uninstalls it to clean up after the check.
     *
     * The function installs the specified app file, identified by its bundle ID, onto the currently booted simulator.
     * It then launches the app and checks the log output for any errors or indicators of failure. If no errors are found,
     * it verifies the presence of both the bundle ID and a process ID (PID) to confirm a successful start.
     * Additionally, it ensures the app's data container is accessible, signaling that the app is fully active.
     * Finally, the function uninstalls the app to leave the simulator in a clean state.
     */
    private suspend fun installAndVerifyAppLaunch(appFile: Path, appBundleId: String) {
        // Step 1: Install the app on the booted simulator
        println("Installing $appBundleId")
        runProcessAndCaptureOutput(
            command = listOf("xcrun", "simctl", "install", "booted", appFile.absolutePathString()),
        )

        // Step 2: Launch the app and capture the output, including any potential errors
        println("Launching $appBundleId")
        val launchOutput = runProcessAndCaptureOutput(
            command = listOf("xcrun", "simctl", "launch", "booted", appBundleId),
            redirectErrorStream = true
        )

        // Step 3: Check for errors in launch output based on specific error keywords
        val errorKeywords = listOf("error", "failed", "FBSOpenApplicationServiceErrorDomain")
        if (errorKeywords.any { launchOutput.stdout.contains(it, ignoreCase = true) }) {
            error("Launch output contains errors. Launch output:\n${launchOutput.stdout}")
        }

        // Step 4: Confirm log output includes both the appBundleId and a PID for successful launch
        if (!Regex("${Regex.escape(appBundleId)}:\\s*(\\d+)")
                .containsMatchIn(launchOutput.stdout)
        ) {
            error("Log output did not confirm successful launch. Launch output:\n${launchOutput.stdout}")
        } else {
            println("Log output indicates successful launch with PID. Output:\n${launchOutput.stdout}")
        }

        // Step 5: Verify the existence of app data container to ensure app is running
        println("Verifying app container existence in data directory")
        val containerDataOutput = runProcessAndCaptureOutput(
            command = listOf("xcrun", "simctl", "get_app_container", "booted", appBundleId, "data"),
            redirectErrorStream = true
        )

        // If data container is missing, the app might not be fully active
        if (containerDataOutput.stdout.length <= 1) {
            error("App container not found in data directory. Container data check failed.")
        } else {
            println("App data container verified successfully.")
        }

        println("App launched and verified successfully!")

        // Step 6: Uninstall the app to clean up after testing
        println("Uninstalling $appBundleId")
        runProcessAndCaptureOutput(command = listOf("xcrun", "simctl", "uninstall", "booted", appBundleId))
    }
}
