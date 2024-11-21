package androidUtils

import org.jetbrains.amper.processes.ProcessLeak
import org.jetbrains.amper.processes.runProcessAndCaptureOutput
import org.jetbrains.amper.test.SimplePrintOutputListener
import org.jetbrains.amper.test.checkExitCodeIsZero
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.pathString

/**
 * Manages APK installation and test execution on the Android emulator.
 * This class provides methods to install test and target APKs on an emulator,
 * configure device settings for testing, and run tests via adb.
 */
object ApkManager  {

    /**
     * Installs the APK located at the given [apkPath] on the emulator using adb.
     *
     * @throws APKNotFoundException if the given APK file does not exist
     */
    suspend fun installApk(apkPath: Path) {
        if (apkPath.exists()) {
            adb("install", "-r", apkPath.pathString)  // "-r" flag allows reinstalling the APK if it's already installed
        } else {
            throw APKNotFoundException("APK file does not exist at path: $apkPath")
        }
    }

    /**
     * Executes an adb command with specified [params] and returns the command's output.
     * Starts a new emulator if one is not already running before executing the command.
     *
     * @return the output from the adb command.
     */
    suspend fun runTestsViaAdb(applicationId: String? = null) {
         //The method first disables all animations on the emulator to speed up test execution.
        adb("shell", "settings", "put", "global", "window_animation_scale", "0.0")
        adb("shell", "settings", "put", "global", "transition_animation_scale", "0.0")
        adb("shell", "settings", "put", "global", "animator_duration_scale", "0.0")
        adb("shell", "settings", "put", "secure", "long_press_timeout", "1000")
        //After it executes tests using the specified test package name,
        // falling back to a default package if none is provided
        val testPackage = applicationId?.let {
            "$it.test/androidx.test.runner.AndroidJUnitRunner"
        } ?: "com.jetbrains.sample.app.test/androidx.test.runner.AndroidJUnitRunner"

        val output = adb("shell", "am", "instrument", "-w", "-r", testPackage)
        if (!output.contains("OK (1 test)") || output.contains("Error")) {
            error("Test failed with output:\n$output")
        }
    }

    /**
    * Executes an adb command with the given [params], and returns the output.
    * If an emulator is not already running, a new one is started before issuing the command
    */
    @OptIn(ProcessLeak::class)
    private suspend fun adb(vararg params: String): String {
        if (!EmulatorManager.isEmulatorRunning()) {
            //If no emulator is currently running, a new one is started before executing the command.
            EmulatorManager.startEmulator()
        }
        val cmd = listOf(EmulatorManager.getAdbPath()) + params

        val result = runProcessAndCaptureOutput(
            command = cmd,
            outputListener = SimplePrintOutputListener,
        ).checkExitCodeIsZero()

        return result.stdout
    }

    /**
     * Exception thrown when an APK file is not found in any of the possible Amper-related paths.
     */
    class APKNotFoundException(message: String) : Exception(message)
}

