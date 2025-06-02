/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package androidUtils

import TestBase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.jetbrains.amper.test.Dirs
import org.jetbrains.amper.test.android.AndroidTools
import org.jetbrains.amper.test.processes.TestReporterProcessOutputListener
import org.jetbrains.amper.test.processes.checkExitCodeIsZero
import java.nio.file.Path
import kotlin.io.path.div
import kotlin.io.path.name
import kotlin.test.fail
import kotlin.time.Duration.Companion.minutes

/**
 * Main test class that provides methods to run Android tests.
 */
open class AndroidBaseTest : TestBase() {

    private val androidTools = runBlocking { AndroidTools.getOrInstallForTests() }

    /**
     * Sets up and executes a test environment for the project located in [projectSource],
     * including assembling the app, optionally using [applicationId] for custom APK setup.
     * The [buildApk] function is used to build the APK via the build tool used for the test.
     *
     * @param projectsDir in-source projects directory for [ProjectSource.Local] projects.
     */
    private fun prepareExecution(
        projectSource: ProjectSource,
        projectsDir: Path,
        applicationId: String? = null,
        buildApk: suspend (projectDir: Path) -> Path,
    ) = runTest(timeout = 15.minutes) {
        val copiedProjectDir = copyProjectToTempDir(projectSource, projectsDir)
        val targetApkPath = buildApk(copiedProjectDir)
        val testAppApkPath = InstrumentedTestApp.assemble(applicationId, testReporter)

        // This dispatcher switch is not superstition. The test dispatcher skips delays by default.
        // We interact with real external processes here, so we can't skip delays when we do retries.
        withContext(Dispatchers.IO) {
            androidTools.ensureEmulatorIsRunning()
            println("Installing test app containing instrumented tests ($testAppApkPath)")
            androidTools.installApk(testAppApkPath)
            println("Installing target app from test project ($targetApkPath)")
            androidTools.installApk(targetApkPath)
            println("Running tests via adb...")
            runTestsViaAdb(applicationId)
        }
    }

    private suspend fun runTestsViaAdb(applicationId: String? = null) {
        // disable all animations on the emulator to speed up test execution.
        adbShell("settings", "put", "global", "window_animation_scale", "0.0")
        adbShell("settings", "put", "global", "transition_animation_scale", "0.0")
        adbShell("settings", "put", "global", "animator_duration_scale", "0.0")
        adbShell("settings", "put", "secure", "long_press_timeout", "1000")
        // After it executes tests using the specified test package name,
        // falling back to a default package if none is provided
        val testAppPackage = applicationId ?: "com.jetbrains.sample.app"
        val testRunnerFqn = "$testAppPackage.test/androidx.test.runner.AndroidJUnitRunner"

        val output = adbShell("am", "instrument", "-w", "-r", testRunnerFqn)
        if (!output.contains("OK (1 test)")) {
            failTestWithAppDiagnostics(output, "Test output doesn't contain 'OK (1 test)'")
        } else if (output.contains("Error")) {
            failTestWithAppDiagnostics(output, "Test failed with 'Error'")
        }
    }

    private suspend fun failTestWithAppDiagnostics(output: String, message: String): Nothing {
        val nSecondsAgo = 15
        val logCatOutput = androidTools.logcatLastNSeconds(nSecondsAgo).prependIndent("[logcat] ")
        fail("$message\n\nEmulator errors/warnings in the last $nSecondsAgo seconds of logs:\n\n${logCatOutput}\n\nTest output:\n\n$output")
    }

    /**
     * Executes the given adb shell [command] and returns the output.
     */
    private suspend fun adbShell(vararg command: String): String {
        val outputListener = TestReporterProcessOutputListener("adb shell", testReporter)
        return androidTools.adb("shell", *command, outputListener = outputListener).checkExitCodeIsZero().stdout
    }

    /**
     * Executes standalone tests for an Android project specified by [projectSource],
     * optionally using [applicationId] for custom APK setups.
     *
     * @param androidAppModuleName android app module name inside the project;
     *   if `null` then the root module is assumed to be the android module.
     */
    internal fun testRunnerStandalone(
        projectSource: ProjectSource,
        applicationId: String? = null,
        androidAppModuleName: String? = null,
    ) {
        val androidTestProjectsPath = Dirs.amperTestProjectsRoot / "android"

        prepareExecution(projectSource, androidTestProjectsPath, applicationId) { projectDir ->
            buildApkWithAmper(projectDir, moduleName = androidAppModuleName ?: projectDir.name)
        }
    }

    /**
     * Builds the Android debug APK for the given [moduleName] in the given [projectDir].
     *
     * @return the path to the built APK
     */
    private suspend fun buildApkWithAmper(projectDir: Path, moduleName: String): Path {
        runAmper(
            workingDir = projectDir,
            args = listOf("task", ":$moduleName:buildAndroidDebug"),
            environment = androidTools.environment() + mapOf(
                "AMPER_NO_GRADLE_DAEMON" to "1", // ensures we don't leak the daemon
            ),
        )
        // internal Amper convention based on the task name
        return projectDir / "build/tasks/_${moduleName}_buildAndroidDebug/gradle-project-debug.apk"
    }
}

private suspend fun AndroidTools.ensureEmulatorIsRunning() {
    if (!isEmulatorRunning()) {
        val testAvdName = "amper-test-avd"
        // If no emulator is currently running, a new one is started before executing the command.
        if (!listAvds().contains(testAvdName)) {
            println("AVD $testAvdName not found, creating a new one...")
            createAvd(testAvdName)
        }
        startAndAwaitEmulator(testAvdName)
    } else {
        println("An emulator is already running, using this one for the tests...")
    }
}
