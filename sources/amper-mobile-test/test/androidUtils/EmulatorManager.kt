package androidUtils

import TestBase
import kotlinx.coroutines.delay
import org.jetbrains.amper.processes.ProcessLeak
import org.jetbrains.amper.processes.runProcessAndCaptureOutput
import org.jetbrains.amper.processes.startLongLivedProcess
import org.jetbrains.amper.test.checkExitCodeIsZero
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.div
import kotlin.io.path.exists
import kotlin.io.path.pathString
import kotlin.time.Duration.Companion.seconds

/**
 * Manages the Android emulator lifecycle and provides helper methods
 * to retrieve paths and available virtual devices.
 */
object EmulatorManager {

    private val isWindows = TestBase().isWindows

    /**
     * Starts the Android emulator with the first available AVD (Android Virtual Device)
     * and waits until it is fully booted.
     *
     * @throws IllegalStateException if no AVDs are available.
     */
    @ProcessLeak
    suspend fun startEmulator() {
        val emulatorPath = getEmulatorPath()
        val availableAvds = getAvailableAvds()

        val avdName = availableAvds.firstOrNull() ?: error("No AVDs available. Please create at least one AVD.")
        println("Run emulator $avdName...")

        startLongLivedProcess(command = listOf(emulatorPath, "-avd", avdName))

        // Wait for the emulator to fully boot
        var isBootComplete = false
        while (!isBootComplete) {
            val result = runProcessAndCaptureOutput(
                command = listOf(getAdbPath(), "shell", "getprop", "sys.boot_completed"),
                redirectErrorStream = true,
            )

            if (result.stdout.trim() == "1") {
                isBootComplete = true
            } else {
                println("Wait emulator run...")
                delay(5.seconds)
            }
        }
    }

    /**
     * Returns whether an Android emulator is currently running.
     */
    suspend fun isEmulatorRunning(): Boolean {
        val result = runProcessAndCaptureOutput(command = listOf(getAdbPath(), "devices")).checkExitCodeIsZero()
        return result.stdout.contains("emulator")
    }

    /**
     * Returns the list of available AVD names.
     *
     * This function uses the `avdmanager list avd` command to get all installed AVDs
     * and parses the output to extract their names.
     */
    private suspend fun getAvailableAvds(): List<String> {
        val avdManagerPath = getAvdManagerPath()
        val result = runProcessAndCaptureOutput(command = listOf(avdManagerPath.pathString, "list", "avd"))
            .checkExitCodeIsZero()

        return result.stdout.lines()
            .filter { it.contains("Name:") }
            .map { it.split("Name:")[1].trim() }
    }

    /**
     * Returns the path of Android Virtual Device Manager
     */
    private fun getAvdManagerPath(): Path {
        val androidHome = System.getenv("ANDROID_HOME")?.let(::Path) ?: error("ANDROID_HOME is not set")

        val avdManagerBinFilename = if (isWindows) "avdmanager.bat" else "avdmanager"
        val possiblePaths = listOf(
            androidHome / "cmdline-tools/latest/bin/$avdManagerBinFilename",
            androidHome / "cmdline-tools/bin/$avdManagerBinFilename",
            androidHome / "tools/bin/$avdManagerBinFilename"
        )

        return possiblePaths.firstOrNull { it.exists() }
            ?: error(
                "$avdManagerBinFilename not found in any of the possible locations:\n" +
                        possiblePaths.joinToString("\n")
            )
    }

    /**
     * Retrieves the path to the `adb` executable, based on the Android SDK location
     * specified by the `ANDROID_HOME` environment variable.
     */
    fun getAdbPath(): String {
        val androidHome = System.getenv("ANDROID_HOME") ?: error("ANDROID_HOME is not set")
        return if (isWindows) {
            "$androidHome\\platform-tools\\adb.exe"
        } else {
            "$androidHome/platform-tools/adb"
        }
    }

    /**
     * Retrieves the path to the `emulator` executable, based on the Android SDK location
     * specified by the `ANDROID_HOME` environment variable.
     */
    private fun getEmulatorPath(): String {
        val androidHome = System.getenv("ANDROID_HOME") ?: error("ANDROID_HOME is not set")
        return if (isWindows) {
            "$androidHome\\emulator\\emulator.exe"
        } else {
            "$androidHome/emulator/emulator"
        }
    }
}

//TODO: Function for emulator finish
