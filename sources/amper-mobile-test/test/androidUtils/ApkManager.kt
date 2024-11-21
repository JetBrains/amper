package androidUtils

import org.jetbrains.amper.processes.ProcessLeak
import org.jetbrains.amper.processes.runProcessAndCaptureOutput
import org.jetbrains.amper.test.SimplePrintOutputListener
import org.jetbrains.amper.test.TestUtil
import org.jetbrains.amper.test.checkExitCodeIsZero
import java.nio.file.Path
import kotlin.io.path.absolutePathString
import kotlin.io.path.div
import kotlin.io.path.exists
import kotlin.io.path.pathString

/**
 * Manages APK installation and test execution on the Android emulator.
 * This class provides methods to install test and target APKs on an emulator,
 * configure device settings for testing, and run tests via adb.
 */
object ApkManager  {

    /**
     * Installs a predefined Android test APK on the emulator by locating the file at a specified path
     * and using adb for installation. Throws an exception if the file is not found.
     *
     * @throws APKNotFoundException when the APK file does not exist at the Amper-related path.
     */
    suspend fun installAndroidTestAPK() {
        val apkPath = TestUtil.amperSourcesRoot / "gradle-e2e-test/testData/projects/test-apk/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk"
        if (apkPath.exists()) {
            adb("install", "-r", apkPath.pathString)  // "-r" flag allows reinstalling the APK if it's already installed
        } else {
            throw APKNotFoundException("APK file does not exist at path: $apkPath")
        }
    }

    /**
     * Installs the target APK for the project located at [projectRootDir] on the emulator by checking multiple paths
     * based on standard and multiplatform project structures, and using adb for installation once the APK is found.
     *
     * @throws APKNotFoundException when the APK file does not exist at any of the checked Amper-related paths.
     */
    suspend fun installTargetAPK(projectRootDir: Path, rootProjectName: String) {
        val gradleApkPath = projectRootDir / "build/outputs/apk/debug/$rootProjectName-debug.apk"
        val standaloneApkPath = projectRootDir / "build/tasks/_${rootProjectName}_buildAndroidDebug/gradle-project-debug.apk"
        val gradleApkPathMultiplatform = projectRootDir / "android-app/build/outputs/apk/debug/android-app-debug.apk"
        val standaloneApkPathMultiplatform = projectRootDir / "build/tasks/_android-app_buildAndroidDebug/gradle-project-debug.apk"

        if (gradleApkPath.exists()) {
            adb("install", "-r", gradleApkPath.pathString)
        } else if (standaloneApkPath.exists()) {
            adb("install", "-r", standaloneApkPath.pathString)
        } else if (gradleApkPathMultiplatform.exists()) {
            adb("install", "-r", gradleApkPathMultiplatform.pathString)
        } else if (standaloneApkPathMultiplatform.exists()) {
            adb("install", "-r", standaloneApkPathMultiplatform.pathString)
        } else {
            throw APKNotFoundException(
                "APK file does not exist at any of these paths:\n" +
                        "${gradleApkPath.absolutePathString()}\n" +
                        "${standaloneApkPath.absolutePathString()}\n" +
                        "${gradleApkPathMultiplatform.absolutePathString()}\n" +
                        standaloneApkPathMultiplatform.absolutePathString()
            )
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

