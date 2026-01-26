/*
 * Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.test.android

import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.amper.processes.ProcessInput
import org.jetbrains.amper.processes.ProcessOutputListener
import org.jetbrains.amper.processes.ProcessResult
import org.jetbrains.amper.processes.runProcess
import org.jetbrains.amper.processes.runProcessAndCaptureOutput
import org.jetbrains.amper.system.info.Arch
import org.jetbrains.amper.system.info.OsFamily
import org.jetbrains.amper.test.Dirs
import org.jetbrains.amper.test.processes.PrefixPrintOutputListener
import org.jetbrains.amper.test.processes.checkExitCodeIsZero
import java.io.File
import java.io.IOException
import java.nio.file.Path
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.concurrent.thread
import kotlin.io.path.absolutePathString
import kotlin.io.path.appendLines
import kotlin.io.path.createDirectories
import kotlin.io.path.createFile
import kotlin.io.path.div
import kotlin.io.path.exists
import kotlin.io.path.pathString
import kotlin.io.path.readLines
import kotlin.random.Random
import kotlin.time.Duration.Companion.seconds
import java.net.ServerSocket

private val binExtension = if (OsFamily.current.isWindows) ".exe" else ""
private val scriptExtension = if (OsFamily.current.isWindows) ".bat" else ""

/**
 * A Kotlin API for Android SDK tools.
 */
class AndroidTools(
    val androidSdkHome: Path,
    // We have to specify the parent directory so we can align the "old" ANDROID_SDK_HOME with ANDROID_USER_HOME.
    // See below how this is used
    private val androidUserHomeParent: Path,
    private val javaHome: Path,
    private val log: (String) -> Unit = ::println,
    /**
     * When true, the ADB server is killed on JVM shutdown.
     * This is useful if [androidSdkHome] needs to be deleted later,
     * otherwise the ADB executable might be in use and prevent deletion.
     */
    private val killAdbOnExit: Boolean = true,
) {
    private val androidUserHome: Path = androidUserHomeParent / ".android"
    private val adbExe: Path = androidSdkHome / "platform-tools/adb$binExtension"
    private val emulatorExe: Path = androidSdkHome / "emulator/emulator$binExtension"

    @Volatile
    private var shutdownHookRegistered = false

    companion object {
        /**
         * Creates an [AndroidTools] instance backed by an Android SDK specifically provisioned for tests.
         * If the SDK is not present, it is provisioned during the call to this function.
         * The provisioned SDK is preserved between test runs and CI builds.
         */
        suspend fun getOrInstallForTests(): AndroidTools = AndroidToolsInstaller.install(
            androidSdkHome = Dirs.androidTestCache / "sdk",
            androidUserHomeParent = Dirs.androidTestCache,
            androidSetupCacheDir = Dirs.androidTestCache / "setup-cache",
        )
    }

    /**
     * Returns a map defining Android-specific environment variables corresponding to these [AndroidTools] setup.
     * This doesn't include `JAVA_HOME`, only `ANDROID_*` variables.
     */
    // See https://developer.android.com/tools/variables
    fun environment(): Map<String, String> = mapOf(
        "ANDROID_HOME" to androidSdkHome.absolutePathString(),
        "ANDROID_USER_HOME" to androidUserHome.absolutePathString(),
        // ANDROID_HOME and ANDROID_USER_HOME should be sufficient if everything else is set to default values.
        // However, the outside environment running the tests might have set more precise env vars (overrides).
        // This could interfere with our test config, so we need to override them here again.
        "ANDROID_SDK_ROOT" to androidSdkHome.absolutePathString(),
        "ANDROID_SDK_HOME" to androidUserHomeParent.absolutePathString(),
        "ANDROID_EMULATOR_HOME" to androidUserHome.absolutePathString(),
        "ANDROID_AVD_HOME" to (androidUserHome / "avd").absolutePathString(),
    )

    private fun findCmdlineToolScript(name: String): Path {
        val possibleBinDirs = listOf(
            androidSdkHome / "cmdline-tools/latest/bin",
            androidSdkHome / "cmdline-tools/bin",
            androidSdkHome / "tools/bin",
        )
        val script = "$name$scriptExtension"
        return possibleBinDirs.map { it / script }.firstOrNull { it.exists() }
            ?: error("$script not found in any of the searched locations:\n${possibleBinDirs.joinToString("\n")}")
    }

    /**
     * Installs the package corresponding to the given [packageName] in this Android SDK.
     */
    suspend fun installSdkPackage(
        packageName: String,
        outputListener: ProcessOutputListener = ProcessOutputListener.NOOP,
    ) {
        log("Installing '$packageName' into '$androidSdkHome'...")
        sdkmanager("--sdk_root=$androidSdkHome", packageName, outputListener = outputListener)
            .checkExitCodeIsZero()
    }

    private suspend fun sdkmanager(
        vararg args: String,
        outputListener: ProcessOutputListener = ProcessOutputListener.NOOP,
    ): ProcessResult = runAndroidSdkProcess(
        executable = findCmdlineToolScript("sdkmanager"),
        args = args,
        outputListener = outputListener
    )

    /**
     * Accepts the Android-related license with the given [name] and [hash].
     */
    fun acceptLicense(name: String, hash: String) {
        log("Accepting license '$name'...")
        val licenseFile = androidSdkHome / "licenses" / name
        licenseFile.parent.createDirectories()

        if (!licenseFile.exists()) {
            licenseFile.createFile()
        }
        if (hash !in licenseFile.readLines()) {
            licenseFile.appendLines(listOf(hash))
        }
    }

    /**
     * Creates a new AVD with the given [name] and configuration.
     */
    suspend fun createAvd(
        name: String,
        apiLevel: Int = 35,
        variant: String = "default",
        arch: String = Arch.current.toEmulatorArch(),
    ) {
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
            executable = findCmdlineToolScript("avdmanager"),
            *args,
            input = input,
            outputListener = PrefixPrintOutputListener("avdmanager"),
        )

    /**
     * Returns whether an Android emulator is currently running.
     */
    suspend fun isEmulatorRunning(): Boolean = adb("devices").checkExitCodeIsZero().stdout.contains("emulator")

    /**
     * Runs the given [block] alongside a new Android emulator with the AVD (Android Virtual Device) identified by the
     * given [avdName].
     *
     * The emulator is guaranteed to be booted before [block] is executed, and to be killed after [block] completes.
     */
    suspend fun <T> withEmulator(avdName: String, block: suspend Emulator.() -> T): T = coroutineScope {
        val port = chooseFreeEmulatorPort()
        val emulatorJob = launch {
            runNewEmulator(avdName, port)
            log("Emulator started")
        }
        val emulator = Emulator(serial = "emulator-$port", androidTools = this@AndroidTools)
        emulator.awaitReady(emulatorJob)

        try {
            block(emulator)
        } finally {
            emulatorJob.cancel()
            emulator.kill()
            log("Awaiting emulator termination for AVD $avdName...")
        }
    }

    private fun chooseFreeEmulatorPort(): Int {
        while (true) {
            val port = Random.nextInt(5554, 5584)
            try {
                ServerSocket(port).close()
                return port
            } catch (_: IOException) {
                continue
            }
        }
    }

    /**
     * Starts the Android emulator with the AVD (Android Virtual Device) identified by the given [avdName], and keeps
     * running until canceled. Upon cancellation, the emulator process will be terminated gracefully.
     */
    private suspend fun runNewEmulator(avdName: String, port: Int) {
        log("Starting emulator for AVD $avdName...")
        runProcess(
            command = listOf(
                emulatorExe.pathString,
                "-port",
                port.toString(),
                "-avd",
                avdName,
                "-read-only", // to not write anything back to the AVD image (enables running multiple emulators with the same AVD)
                "-no-window", // required on CI, otherwise the device fails to start because of the absence of UI
                "-no-metrics", // needs to be explicitly disabled to avoid interactive prompts in the future
                "-no-snapshot", // avoids saving/restoring the emulator state (we don't need it)
                "-no-boot-anim", // faster startup
                "-no-audio", // audio isn't needed
                "-wipe-data", // start fresh each time we launch the test suite
            ),
            environment = environment() + mapOf(
                "JAVA_HOME" to javaHome.pathString,
                // apparently, the emulator needs to have these tools on the PATH
                "PATH" to envPathWithPrepended(
                    androidSdkHome / "emulator",
                    androidSdkHome / "platform-tools",
                ),
            ),
            // we can't ignore stdout because some startup errors are printed there (e.g. absence of window)
            outputListener = PrefixPrintOutputListener("emulator"),
        )
    }

    private fun envPathWithPrepended(vararg path: Path): String =
        (path.map { it.pathString } + listOf(System.getenv("PATH"))).joinToString(File.pathSeparator)

    private suspend fun Emulator.awaitReady(emulatorJob: Job) {
        log("Waiting for the emulator to boot...")
        while (true) {
            if (!emulatorJob.isActive) {
                error("The emulator could not start properly, check errors above")
            }
            val result = adb("shell", "getprop", "sys.boot_completed")

            // Right after starting the emulator, we first get non-zero exit codes with errors on stderr:
            //    - "no devices/emulators found" (for a couple seconds)
            //    - "device offline" (for a couple seconds)
            // Then we get exit code 0 with output "1" (for the sys.boot_completed property)
            if (result.exitCode == 0 && result.stdout.trim() == "1") {
                return
            } else {
                log("  (still booting...)")
                delay(5.seconds)
            }
        }
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
    ): ProcessResult {
        if (!shutdownHookRegistered && killAdbOnExit) {
            registerAdbShutdownOnExit()
        }
        return runAndroidSdkProcess(adbExe, *command, outputListener = outputListener)
    }

    private fun registerAdbShutdownOnExit() {
        shutdownHookRegistered = true
        Runtime.getRuntime().addShutdownHook(thread(start = false) {
            // We cannot use async-process helpers while the JVM is shutting down, because they are automatically
            // cleaned up on JVM exit, so it would immediately fail.
            // This is why we use a plain ProcessBuilder here.
            @Suppress("SSBasedInspection")
            ProcessBuilder(adbExe.pathString, "kill-server").inheritIO().start()
        })
    }

    private suspend fun runAndroidSdkProcess(
        executable: Path,
        vararg args: String,
        input: ProcessInput = ProcessInput.Empty,
        outputListener: ProcessOutputListener,
    ): ProcessResult = runProcessAndCaptureOutput(
        command = listOf(executable.pathString) + args,
        environment = environment() + mapOf("JAVA_HOME" to javaHome.pathString),
        input = input,
        outputListener = outputListener,
    )
}

class Emulator(
    val serial: String,
    private val androidTools: AndroidTools,
) {
    suspend fun adb(
        vararg args: String,
        outputListener: ProcessOutputListener = ProcessOutputListener.NOOP,
    ) = androidTools.adb("-s", serial, *args, outputListener = outputListener)

    /**
     * Installs the APK located at the given [apkPath] on the emulator using adb.
     */
    suspend fun installApk(apkPath: Path) {
        check(apkPath.exists()) { "APK file not found at path: $apkPath" }
        adb(
            "install", "-r", apkPath.pathString,  // "-r" flag allows reinstalling the APK if it's already installed
            outputListener = PrefixPrintOutputListener("adb install"),
        ).checkExitCodeIsZero()
    }

    /**
     * Uninstalls the application with the given [packageName].
     *
     * If [ignoreFailures] is true (the default), the exit code of the `adb uninstall` command is not checked.
     * This is useful to avoid failing if the package is not found (already not installed).
     */
    suspend fun uninstall(packageName: String, ignoreFailures: Boolean = true) {
        val result = adb("uninstall", packageName, outputListener = PrefixPrintOutputListener("adb uninstall"))
        if (!ignoreFailures) {
            result.checkExitCodeIsZero()
        }
    }

    /**
     * Returns the last [nSeconds] seconds of logcat output at warning level or above.
     */
    suspend fun logcatLastNSeconds(nSeconds: Int): String {
        val formattedTime = LocalDateTime.now().minusSeconds(nSeconds.toLong())
            .format(DateTimeFormatter.ofPattern("MM-dd HH:mm:ss.SSS"))
        return adb("logcat", "*:W", "-d", "-t", formattedTime).checkExitCodeIsZero().stdout
    }

    /**
     * Kills this emulator's process.
     */
    suspend fun kill() {
        adb("emu", "kill").checkExitCodeIsZero()
    }
}

fun Arch.toEmulatorArch(): String = when(this) {
    Arch.X64 -> "x86_64"
    Arch.Arm64 -> "arm64-v8a"
}
