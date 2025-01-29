/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.test.android

import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.amper.core.system.Arch
import org.jetbrains.amper.core.system.DefaultSystemInfo
import org.jetbrains.amper.processes.ProcessInput
import org.jetbrains.amper.processes.ProcessLeak
import org.jetbrains.amper.processes.ProcessOutputListener
import org.jetbrains.amper.processes.ProcessResult
import org.jetbrains.amper.processes.runProcess
import org.jetbrains.amper.processes.runProcessAndCaptureOutput
import org.jetbrains.amper.test.Dirs
import org.jetbrains.amper.test.checkExitCodeIsZero
import java.io.File
import java.nio.file.Path
import kotlin.concurrent.thread
import kotlin.io.path.absolutePathString
import kotlin.io.path.appendLines
import kotlin.io.path.createDirectories
import kotlin.io.path.createFile
import kotlin.io.path.div
import kotlin.io.path.exists
import kotlin.io.path.pathString
import kotlin.io.path.readLines
import kotlin.time.Duration.Companion.seconds

private val binExtension = if (DefaultSystemInfo.detect().family.isWindows) ".exe" else ""
private val scriptExtension = if (DefaultSystemInfo.detect().family.isWindows) ".bat" else ""

/**
 * A Kotlin API for Android SDK tools.
 */
class AndroidTools(
    val androidSdkHome: Path,
    private val androidUserHome: Path,
    private val javaHome: Path,
    private val log: (String) -> Unit = ::println,
    /**
     * When true, the ADB server is killed on JVM shutdown.
     * This is useful if [androidSdkHome] needs to be deleted later,
     * otherwise the ADB executable might be in use and prevent deletion.
     */
    private val killAdbOnExit: Boolean = true,
) {
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
            androidUserHome = Dirs.androidTestCache / "user-home",
            androidSetupCacheDir = Dirs.androidTestCache / "setup-cache",
        )
    }

    /**
     * Returns a map defining Android-specific environment variables corresponding to these [AndroidTools] setup.
     * This doesn't include `JAVA_HOME`, only `ANDROID_*` variables.
     */
    fun environment(): Map<String, String> = mapOf(
        "ANDROID_HOME" to androidSdkHome.absolutePathString(),
        "ANDROID_SDK_HOME" to androidSdkHome.absolutePathString(),
        "ANDROID_USER_HOME" to androidUserHome.absolutePathString(),
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
        arch: String = DefaultSystemInfo.detect().arch.toEmulatorArch(),
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
        runAndroidSdkProcess(executable = findCmdlineToolScript("avdmanager"), *args, input = input)

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
    @OptIn(DelicateCoroutinesApi::class)
    @ProcessLeak
    suspend fun startAndAwaitEmulator(avdName: String) {
        log("Starting emulator for AVD $avdName...")

        // We launch it in a long-lived coroutine to reuse the emulator between tests.
        // It will be killed on JVM exit thanks to runProcess's default behavior.
        val emulatorJob = GlobalScope.launch {
            runProcess(
                // The -no-window option is required on CI, otherwise the device fails to start
                command = listOf(emulatorExe.pathString, "-avd", avdName, "-no-window"),
                environment = environment() + mapOf(
                    "JAVA_HOME" to javaHome.pathString,
                    // apparently, the emulator needs to have these tools on the PATH
                    "PATH" to envPathWithPrepended(
                        androidSdkHome / "emulator",
                        androidSdkHome / "platform-tools",
                    ),
                ),
                outputListener = object : ProcessOutputListener {
                    // we can't ignore stdout because some startup errors are printed there (e.g. absence of window)
                    override fun onStdoutLine(line: String, pid: Long) {
                        @Suppress("ReplacePrintlnWithLogging")
                        println("[emulator out] $line")
                    }
                    override fun onStderrLine(line: String, pid: Long) {
                        @Suppress("ReplacePrintlnWithLogging") // ok for tests
                        println("[emulator error] $line")
                    }
                }
            )
        }

        awaitEmulatorReady(emulatorJob)
    }

    private fun envPathWithPrepended(vararg path: Path): String =
        (path.map { it.pathString } + listOf(System.getenv("PATH"))).joinToString(File.pathSeparator)

    private suspend fun awaitEmulatorReady(emulatorJob: Job) {
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
    ): ProcessResult {
        if (!shutdownHookRegistered && killAdbOnExit) {
            registerAdbShutdownOnExit()
        }
        return runAndroidSdkProcess(adbExe, *command, outputListener = outputListener)
    }

    private fun registerAdbShutdownOnExit() {
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
        outputListener: ProcessOutputListener = ProcessOutputListener.NOOP,
    ): ProcessResult = runProcessAndCaptureOutput(
        command = listOf(executable.pathString) + args,
        environment = environment() + mapOf("JAVA_HOME" to javaHome.pathString),
        input = input,
        outputListener = outputListener,
    )
}

fun Arch.toEmulatorArch(): String = when(this) {
    Arch.X64 -> "x86_64"
    Arch.Arm64 -> "arm64-v8a"
}
