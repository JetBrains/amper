import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Path
import kotlin.concurrent.thread
import kotlin.io.path.Path
import kotlin.io.path.absolutePathString
import kotlin.io.path.div
import kotlin.io.path.exists
import kotlin.io.path.pathString

/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

open class AndroidBaseTest : TestBase() {
    val sessionInfoPath = "./scripts/device.session.json"

    private fun prepareExecution(
        projectName: String,
        projectPath: String,
        applicationId: String? = null,
        projectAction: (String) -> Unit,
    ) {
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

    internal fun testRunnerStandalone(projectName: String, applicationId: String? = null) =
        prepareExecution(projectName, "../../sources/amper-backend-test/testData/projects/android", applicationId) {
            runAmper(
                workingDir = destinationBasePath.resolve(it),
                args = listOf("task", ":$it:buildAndroidDebug"),
            )
        }

    internal fun testRunnerGradle(projectName: String) =
        prepareExecution(projectName, "../../sources/gradle-e2e-test/testData/projects") {
            prepareProjectsAndroidForGradle(it)
        }

    private fun prepareProjectsAndroidForGradle(projectName: String) {
        val projectDirectory = File("tempProjects" + File.separator + projectName)
        val runWithPluginClasspath = true
        putAmperToGradleFile(projectDirectory, runWithPluginClasspath)
        assembleTargetApp(projectDirectory)
    }

    private fun getAdbRemoteSession(): String {
        val output = executeCommand(
            command = listOf("bash", "-c", "./scripts/espressoSession.sh -s $sessionInfoPath port"),
        )

        if (output.isEmpty()) {
            error("Session wasn't created!")
        }
        return output
    }

    private fun adb(vararg params: String): ByteArrayOutputStream {
        val stdout = ByteArrayOutputStream()
        val stderr = ByteArrayOutputStream()
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

        executeCommand(
            command = cmd,
            standardOut = stdout,
            standardErr = stderr
        )

        val cmdOutput = stdout.toString()
        val cmdError = stderr.toString()
        println(cmdOutput)
        println(cmdError)

        return stdout
    }
    private fun isRunningInTeamCity(): Boolean {
        return System.getenv("TEAMCITY_VERSION") != null
    }

    private fun createAdbRemoteSession() {
        val stdout = executeCommand(
            command = listOf(
                "./scripts/espressoSession.sh",
                "-s",
                sessionInfoPath,
                "-n",
                "Amper UI Tests",
                "create"
            ),
            expectExitCodeZero = false, // when a session is already created, the script returns exit code 1
        )
        println(stdout)
    }

    fun deleteAdbRemoteSession() {
        val stdout = executeCommand(
            command = listOf(
                "bash",
                "-c",
                """./scripts/espressoSession.sh -s $sessionInfoPath -n \"Amper UI Tests\" delete"""
            ),
        )
        println(stdout)
    }

    private fun assembleTestApp(applicationId: String? = null) {
        val testFilePath = "../gradle-e2e-test/testData/projects/test-apk/app/src/androidTest/java/com/jetbrains/sample/app/ExampleInstrumentedTest.kt"
        val buildFilePath = "../gradle-e2e-test/testData/projects/test-apk/app/build.gradle.kts"
        var originalTestFileContent: String? = null
        var originalBuildFileContent: String? = null

        applicationId?.let {
            // Step 1: Modify the test file (ExampleInstrumentedTest.kt)
            originalTestFileContent = File(testFilePath).readText()
            val updatedTestFileContent = originalTestFileContent?.replace(
                "com.jetbrains.sample.app",
                applicationId
            )
            File(testFilePath).writeText(updatedTestFileContent ?: "")

            // Step 2: Modify the build.gradle.kts
            originalBuildFileContent = File(buildFilePath).readText()
            val updatedBuildFileContent = originalBuildFileContent?.replace(
                "applicationId = \"com.jetbrains.sample.app\"",
                "applicationId = \"$applicationId\""
            )?.replace(
                "testApplicationId = \"com.jetbrains.sample.app.test\"",
                "testApplicationId = \"$applicationId.test\""
            )
            File(buildFilePath).writeText(updatedBuildFileContent ?: "")
        }

        val gradlewPath = if (isWindows) "../../gradlew.bat" else "../../gradlew"

        // Run the APK build command
        val stdout = executeCommand(
            command = listOf(
                gradlewPath,
                "-p",
                "../gradle-e2e-test/testData/projects/test-apk",
                "createDebugAndroidTestApk"
            ),
        )

        println(stdout)

        // Step 3: Restore the original content of the test file and build.gradle.kts
        originalTestFileContent?.let {
            File(testFilePath).writeText(it)
        }

        originalBuildFileContent?.let {
            File(buildFilePath).writeText(it)
        }
    }


    private fun installAndroidTestAPK() {
        val apkPath =
            "../gradle-e2e-test/testData/projects/test-apk/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk"
        val apkFile = File(apkPath)
        if (apkFile.exists()) {
            adb("install", "-r", apkPath)
        } else {
            throw APKNotFoundException("APK file does not exist at path: $apkPath")
        }
    }

    private fun installTargetAPK(projectName: String) {
        val gradleApkPath = Path("tempProjects/$projectName/build/outputs/apk/debug/$projectName-debug.apk")
        val standaloneApkPath = Path("tempProjects/$projectName/build/tasks/_${projectName}_buildAndroidDebug/gradle-project-debug.apk")
        val gradleApkPathMultiplatform = Path("tempProjects/$projectName/android-app/build/outputs/apk/debug/android-app-debug.apk")

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

    private fun runTestsViaAdb(applicationId: String? = null) {
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
        val output = adb(
            "shell",
            "am",
            "instrument",
            "-w",
            "-r",
            testPackage
        ).toString(Charsets.UTF_8)

        // Check the output for success or errors
        if (!output.contains("OK (1 test)") || output.contains("Error")) {
            error("Test failed with output:\n$output")
        }
    }

    class APKNotFoundException(message: String) : Exception(message)

    private fun isEmulatorRunning(): Boolean {
        val output = executeCommand(command = listOf(getAdbPath(), "devices"))
        return output.contains("emulator")
    }


    private fun getAvailableAvds(): List<String> {
        val avdManagerPath = getAvdManagerPath()
        val output = executeCommand(command = listOf(avdManagerPath.pathString, "list", "avd"))

        return output.lines()
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

    private fun startEmulator() {
        val emulatorPath = getEmulatorPath()
        val availableAvds = getAvailableAvds()

        if (availableAvds.isEmpty()) {
            error("No AVDs available. Please create at least one AVD.")
        }

        val avdName = availableAvds[0]
        println("Run emulator $avdName...")

        val processBuilder = ProcessBuilder(emulatorPath, "-avd", avdName)
        processBuilder.redirectErrorStream(true)
        val process = processBuilder.start()

        // we need to consume the output of the process otherwise it may be stuck on full buffer
        thread(isDaemon = true) {
            process.inputStream.bufferedReader(Charsets.UTF_8).useLines { sequence ->
                sequence.forEach {}
            }
        }

        var isBootComplete = false
        while (!isBootComplete) {
            val output = executeCommand(
                command = listOf(getAdbPath(), "shell", "getprop", "sys.boot_completed"),
                expectExitCodeZero = false, // sometimes we get "no devices/emulators found" before the emulator starts
            )

            if (output == "1") {
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



