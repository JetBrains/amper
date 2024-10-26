import kotlinx.coroutines.runBlocking
import org.jetbrains.amper.processes.ProcessLeak
import org.jetbrains.amper.processes.runProcess
import org.jetbrains.amper.processes.runProcessAndCaptureOutput
import org.jetbrains.amper.processes.startLongLivedProcess
import org.jetbrains.amper.test.SimplePrintOutputListener
import org.jetbrains.amper.test.TestUtil
import org.jetbrains.amper.test.checkExitCodeIsZero
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.absolutePathString
import kotlin.io.path.div
import kotlin.io.path.exists
import kotlin.io.path.pathString
import kotlin.io.path.readText
import kotlin.io.path.writeText

/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

open class AndroidBaseTest : TestBase() {
    private val sessionInfoPath = scriptsDir / "device.session.json"
    private val espressoScriptPath = scriptsDir / "espressoSession.sh"
    private val gradleE2eTestProjectsPath = TestUtil.amperSourcesRoot / "gradle-e2e-test/testData/projects"

    private fun prepareExecution(
        projectName: String,
        projectPath: Path,
        applicationId: String? = null,
        projectAction: suspend (String) -> Unit,
    ) = runBlocking {
        copyProject(projectName, projectPath)
        projectAction(projectName)
        assembleTestApp(applicationId)
        if (isRunningInTeamCity()) {
            createAdbRemoteSession()
        }
        installAndroidTestAPK()
        installTargetAPK(projectName)
        runTestsViaAdb(applicationId)
    }

    internal fun testRunnerStandalone(projectName: String, applicationId: String? = null) {
        val androidTestProjectsPath = TestUtil.amperSourcesRoot.resolve("amper-backend-test/testData/projects/android")
        prepareExecution(projectName, androidTestProjectsPath, applicationId) {
            runAmper(
                workingDir = tempProjectsDir.resolve(it),
                args = listOf("task", ":$it:buildAndroidDebug"),
            )
        }
    }

    internal fun testRunnerGradle(projectName: String) {
        prepareExecution(projectName, gradleE2eTestProjectsPath) {
            prepareProjectsAndroidForGradle(it)
        }
    }

    private suspend fun prepareProjectsAndroidForGradle(projectName: String) {
        val projectDirectory = tempProjectsDir / projectName
        val runWithPluginClasspath = true
        putAmperToGradleFile(projectDirectory, runWithPluginClasspath)
        assembleTargetApp(projectDirectory)
    }

    private suspend fun getAdbRemoteSession(): String {
        val result = runProcessAndCaptureOutput(
            command = listOf("bash", "-c", "$espressoScriptPath -s $sessionInfoPath port"),
            redirectErrorStream = true,
        ).checkExitCodeIsZero()

        if (result.stdout.isBlank()) {
            error("Session wasn't created!")
        }
        return result.stdout.trim()
    }

    private suspend fun adb(vararg params: String): String {
        val cmd = mutableListOf<String>()

        if (!isRunningInTeamCity()) {
            if (!isEmulatorRunning()) {
                startEmulator()
            }
            cmd.add(getAdbPath())
        } else {
            val adbCompanion = getAdbRemoteSession()
            val host = adbCompanion.split(':')
            cmd.apply {
                add(getAdbPath())
                add("-H")
                add(host[0])
                add("-P")
                add(host[1])
                // add("-s", "emulator-5560") // Uncomment this line if needed
            }
        }

        cmd.addAll(params)

        val result = runProcessAndCaptureOutput(
            command = cmd,
            outputListener = SimplePrintOutputListener(),
        ).checkExitCodeIsZero()

        return result.stdout
    }

    private fun isRunningInTeamCity(): Boolean {
        return System.getenv("TEAMCITY_VERSION") != null
    }

    private suspend fun createAdbRemoteSession() {
        // When a session is already created, the script returns exit code 1, so we don't want to fail in that case.
        // This is why we don't check the exit code here.
        runProcess(
            command = listOf(
                espressoScriptPath.pathString,
                "-s",
                sessionInfoPath.pathString,
                "-n",
                "Amper UI Tests",
                "create"
            ),
            outputListener = SimplePrintOutputListener(),
        )
    }

    suspend fun deleteAdbRemoteSession() {
        runProcessAndCaptureOutput(
            command = listOf(
                "bash",
                "-c",
                """$espressoScriptPath -s $sessionInfoPath -n \"Amper UI Tests\" delete"""
            ),
            outputListener = SimplePrintOutputListener(),
        ).checkExitCodeIsZero()
    }

    private suspend fun assembleTestApp(applicationId: String? = null) {
        val testApkAppProjectPath = gradleE2eTestProjectsPath / "test-apk/app"
        val testFilePath = testApkAppProjectPath / "src/androidTest/java/com/jetbrains/sample/app/ExampleInstrumentedTest.kt"
        val buildFilePath = testApkAppProjectPath / "build.gradle.kts"
        var originalTestFileContent: String? = null
        var originalBuildFileContent: String? = null

        if (applicationId != null) {
            // Step 1: Modify the test file (ExampleInstrumentedTest.kt)
            originalTestFileContent = testFilePath.readText()
            val updatedTestFileContent = originalTestFileContent.replace("com.jetbrains.sample.app", applicationId)
            testFilePath.writeText(updatedTestFileContent)

            // Step 2: Modify the build.gradle.kts
            originalBuildFileContent = buildFilePath.readText()
            val updatedBuildFileContent = originalBuildFileContent.replace(
                "applicationId = \"com.jetbrains.sample.app\"",
                "applicationId = \"$applicationId\""
            ).replace(
                "testApplicationId = \"com.jetbrains.sample.app.test\"",
                "testApplicationId = \"$applicationId.test\""
            )
            buildFilePath.writeText(updatedBuildFileContent)
        }

        val gradlewFilename = if (isWindows) "gradlew.bat" else "gradlew"
        val gradlewPath = TestUtil.amperCheckoutRoot / gradlewFilename

        // Run the APK build command
        runProcessAndCaptureOutput(
            command = listOf(
                gradlewPath.pathString,
                "-p",
                testApkAppProjectPath.pathString,
                "createDebugAndroidTestApk"
            ),
            outputListener = SimplePrintOutputListener(),
        ).checkExitCodeIsZero()

        // Step 3: Restore the original content of the test file and build.gradle.kts
        originalTestFileContent?.let {
            testFilePath.writeText(it)
        }

        originalBuildFileContent?.let {
            buildFilePath.writeText(it)
        }
    }


    private suspend fun installAndroidTestAPK() {
        val apkPath = gradleE2eTestProjectsPath / "test-apk/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk"
        if (apkPath.exists()) {
            adb("install", "-r", apkPath.pathString)
        } else {
            throw APKNotFoundException("APK file does not exist at path: $apkPath")
        }
    }

    private suspend fun installTargetAPK(projectName: String) {
        val gradleApkPath = tempProjectsDir / "$projectName/build/outputs/apk/debug/$projectName-debug.apk"
        val standaloneApkPath = tempProjectsDir / "$projectName/build/tasks/_${projectName}_buildAndroidDebug/gradle-project-debug.apk"
        val gradleApkPathMultiplatform = tempProjectsDir / "$projectName/android-app/build/outputs/apk/debug/android-app-debug.apk"

        if (gradleApkPath.exists()) {
            adb("install", "-r", gradleApkPath.pathString)
        } else if (standaloneApkPath.exists()) {
            adb("install", "-r", standaloneApkPath.pathString)
        } else if (gradleApkPathMultiplatform.exists()) {
            adb("install", "-r", gradleApkPathMultiplatform.pathString)
        } else {
            throw APKNotFoundException("APK file does not exist at any of those paths:\n" +
                    "${gradleApkPath.absolutePathString()}\n" +
                    "${standaloneApkPath.absolutePathString()}\n" +
                    gradleApkPathMultiplatform.absolutePathString()
            )
        }
    }

    private suspend fun runTestsViaAdb(applicationId: String? = null) {
        // Disable animations for testing
        adb("shell", "settings", "put", "global", "window_animation_scale", "0.0")
        adb("shell", "settings", "put", "global", "transition_animation_scale", "0.0")
        adb("shell", "settings", "put", "global", "animator_duration_scale", "0.0")
        adb("shell", "settings", "put", "secure", "long_press_timeout", "1000")

        // Determine the package to use for the test runner
        val testPackage = applicationId?.let {
            "$it.test/androidx.test.runner.AndroidJUnitRunner"
        } ?: "com.jetbrains.sample.app.test/androidx.test.runner.AndroidJUnitRunner"

        // Run the instrumentation tests
        val output = adb("shell", "am", "instrument", "-w", "-r", testPackage)

        // Check the output for success or errors
        if (!output.contains("OK (1 test)") || output.contains("Error")) {
            error("Test failed with output:\n$output")
        }
    }

    class APKNotFoundException(message: String) : Exception(message)

    private suspend fun isEmulatorRunning(): Boolean {
        val result = runProcessAndCaptureOutput(command = listOf(getAdbPath(), "devices")).checkExitCodeIsZero()
        return result.stdout.contains("emulator")
    }


    private suspend fun getAvailableAvds(): List<String> {
        val avdManagerPath = getAvdManagerPath()
        val result = runProcessAndCaptureOutput(command = listOf(avdManagerPath.pathString, "list", "avd"))
            .checkExitCodeIsZero()

        return result.stdout.lines()
            .filter { it.contains("Name:") }
            .map { it.split("Name:")[1].trim() }
    }

    private fun getAvdManagerPath(): Path {
        val androidHome = System.getenv("ANDROID_HOME")?.let(::Path) ?: error("ANDROID_HOME is not set")

        val avdManagerBinFilename = if (isWindows) "avdmanager.bat" else "avdmanager"
        val possiblePaths = listOf(
            androidHome / "cmdline-tools/latest/bin/$avdManagerBinFilename",
            androidHome / "cmdline-tools/bin/$avdManagerBinFilename",
            androidHome / "tools/bin/$avdManagerBinFilename"
        )

        return possiblePaths.firstOrNull { it.exists() }
            ?: error("$avdManagerBinFilename not found in any of the possible locations:\n" +
                    possiblePaths.joinToString("\n"))
    }

    @OptIn(ProcessLeak::class) // TODO can we somehow shutdown the emulator after all tests?
    private suspend fun startEmulator() {
        val emulatorPath = getEmulatorPath()
        val availableAvds = getAvailableAvds()

        if (availableAvds.isEmpty()) {
            error("No AVDs available. Please create at least one AVD.")
        }

        val avdName = availableAvds[0]
        println("Run emulator $avdName...")

        startLongLivedProcess(command = listOf(emulatorPath, "-avd", avdName))

        var isBootComplete = false
        while (!isBootComplete) {
            // Note: sometimes we get exit code 1 with "no devices/emulators found" (before the emulator starts).
            // We don't fail on non-zero exit code here because of this.
            val result = runProcessAndCaptureOutput(
                command = listOf(getAdbPath(), "shell", "getprop", "sys.boot_completed"),
                redirectErrorStream = true,
            )

            if (result.stdout.trim() == "1") {
                isBootComplete = true
            } else {
                println("Wait emulator run...")
                Thread.sleep(5000)
            }
        }
    }

    private fun getAdbPath(): String {
        val androidHome = System.getenv("ANDROID_HOME") ?: error("ANDROID_HOME is not set")
        return if (isWindows) {
            "$androidHome\\platform-tools\\adb.exe"
        } else {
            "$androidHome/platform-tools/adb"
        }
    }

    private fun getEmulatorPath(): String {
        val androidHome = System.getenv("ANDROID_HOME") ?: error("ANDROID_HOME is not set")
        return if (isWindows) {
            "$androidHome\\emulator\\emulator.exe"
        } else {
            "$androidHome/emulator/emulator"
        }
    }
}
