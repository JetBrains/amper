/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package androidUtils

import kotlinx.coroutines.delay
import org.jetbrains.amper.core.system.DefaultSystemInfo
import org.jetbrains.amper.processes.ProcessInput
import org.jetbrains.amper.processes.ProcessLeak
import org.jetbrains.amper.processes.ProcessOutputListener
import org.jetbrains.amper.processes.ProcessResult
import org.jetbrains.amper.processes.runProcessAndCaptureOutput
import org.jetbrains.amper.processes.startLongLivedProcess
import org.jetbrains.amper.test.checkExitCodeIsZero
import java.io.File
import java.nio.file.Path
import kotlin.concurrent.thread
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.io.path.div
import kotlin.io.path.exists
import kotlin.io.path.pathString
import kotlin.time.Duration.Companion.seconds

private val binExtension = if (DefaultSystemInfo.detect().family.isWindows) ".exe" else ""
private val scriptExtension = if (DefaultSystemInfo.detect().family.isWindows) ".bat" else ""

class AndroidTools(
    private val androidHome: Path,
    /**
     * When true, the ADB server is killed on JVM shutdown.
     * This is useful if [androidHome] needs to be deleted later,
     * otherwise the ADB executable might be in use and prevent deletion.
     */
    killAdbOnExit: Boolean = true,
) {
    private val adbExe: Path = androidHome / "platform-tools/adb$binExtension"
    private val emulatorExe: Path = androidHome / "emulator/emulator$binExtension"
    private val avdManagerScript by lazy { findAvdManagerScript(androidHome) }

    init {
        if (killAdbOnExit) {
            Runtime.getRuntime().addShutdownHook(thread(start = false) {
                // We cannot use async-process helpers while the JVM is shutting down, because they are automatically
                // cleaned up on JVM exit, so it would immediately fail.
                // This is why we use a plain ProcessBuilder here.
                @Suppress("SSBasedInspection")
                ProcessBuilder(adbExe.pathString, "kill-server").inheritIO().start()
            })
        }
    }

    /**
     * Returns whether an Android emulator is currently running.
     */
    suspend fun isEmulatorRunning(): Boolean = adb("devices").checkExitCodeIsZero().stdout.contains("emulator")

    /**
     * Starts the Android emulator with the given AVD (Android Virtual Device) and waits until it is fully booted.
     * Warning: the emulator keeps running after the JVM exits.
     *
     * @return the PID of the emulator process
     */
    @OptIn(ExperimentalEncodingApi::class)
    @ProcessLeak
    suspend fun startAndAwaitEmulator(avdName: String): Long {
        println("Starting emulator for AVD $avdName...")

        val pid = startLongLivedProcess(
            command = listOf(emulatorExe.pathString, "-avd", avdName),
            environment = mapOf(
                "ANDROID_HOME" to androidHome.pathString,
                // apparently, the emulator needs to have these tools on the PATH
                "PATH" to envPathWithPrepended(
                    androidHome / "emulator",
                    androidHome / "platform-tools",
                ),
            ),
        )

        awaitEmulatorReady()
        return pid
    }

    private fun envPathWithPrepended(vararg path: Path): String =
        (path.map { it.pathString } + listOf(System.getenv("PATH"))).joinToString(File.pathSeparator)

    private suspend fun awaitEmulatorReady() {
        println("Waiting for the emulator to boot...")
        while (true) {
            val result = adb("shell", "getprop", "sys.boot_completed")

            // Right after starting the emulator, we first get non-zero exit codes with errors on stderr:
            //    - "no devices/emulators found" (for a couple seconds)
            //    - "device offline" (for a couple seconds)
            // Then we get exit code 0 with output "1" (for the sys.boot_completed property)
            if (result.exitCode == 0 && result.stdout.trim() == "1") {
                return
            } else {
                println("  (still booting...)")
                delay(5.seconds)
            }
        }
    }

    /**
     * Installs the APK located at the given [apkPath] on the emulator using adb.
     */
    suspend fun installApk(apkPath: Path) {
        check(apkPath.exists()) { "APK file not found at path: $apkPath" }
        adb("install", "-r", apkPath.pathString)  // "-r" flag allows reinstalling the APK if it's already installed
    }

    /**
     * Runs the given ADB [command].
     *
     * The exit code and entire output is captured in the returned [ProcessResult].
     * If an [outputListener] is provided, it will receive the ADB process output lines while the process is running.
     */
    suspend fun adb(
        vararg command: String,
        outputListener: ProcessOutputListener = ProcessOutputListener.NOOP,
    ): ProcessResult = runAndroidSdkProcess(adbExe, *command, outputListener = outputListener)

    /**
     * Creates a new AVD with the given [name] and configuration.
     */
    suspend fun createAvd(name: String, apiLevel: Int = 35, variant: String = "default", arch: String = "x86_64") {
        // Note: this modifies .knownPackages, which might be important for caching
        avdmanager(
            "create", "avd", "-n", name, "-k", "system-images;android-$apiLevel;$variant;$arch",
            input = ProcessInput.Text("no\n"), // Do you wish to create a custom hardware profile? [no]
        ).checkExitCodeIsZero()
    }

    /**
     * Returns the list of available AVD names.
     */
    suspend fun listAvds(): List<String> = avdmanager("list", "avd", "-c")
        .checkExitCodeIsZero().stdout
        .lines()
        .filter { it.isNotBlank() }

    private suspend fun avdmanager(vararg args: String, input: ProcessInput = ProcessInput.Empty): ProcessResult =
        runAndroidSdkProcess(
            executable = avdManagerScript,
            *args,
            input = input,
        )

    private suspend fun runAndroidSdkProcess(
        executable: Path,
        vararg args: String,
        input: ProcessInput = ProcessInput.Empty,
        outputListener: ProcessOutputListener = ProcessOutputListener.NOOP,
    ): ProcessResult = runProcessAndCaptureOutput(
        command = listOf(executable.pathString) + args,
        environment = mapOf("ANDROID_HOME" to androidHome.pathString),
        input = input,
        outputListener = outputListener,
    )
}

private fun findAvdManagerScript(androidHome: Path): Path {
    val possibleBinDirs = listOf(
        androidHome / "cmdline-tools/latest/bin",
        androidHome / "cmdline-tools/bin",
        androidHome / "tools/bin",
    )
    val scriptName = "avdmanager$scriptExtension"
    return possibleBinDirs.map { it / scriptName }.firstOrNull { it.exists() }
        ?: error("$scriptName not found in any of the searched locations:\n${possibleBinDirs.joinToString("\n")}")
}
