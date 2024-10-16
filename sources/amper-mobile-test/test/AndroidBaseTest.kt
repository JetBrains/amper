import org.junit.jupiter.api.condition.OS
import java.io.ByteArrayOutputStream
import java.io.File

/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

open class AndroidBaseTest : TestBase() {
    val sessionInfoPath = "./scripts/device.session.json"

    // Overloaded method with applicationId
    private fun prepareExecution(
        projectName: String,
        projectPath: String,
        projectAction: (String) -> Unit,
        applicationId: String? = null
    ) {
        copyProject(projectName, projectPath)
        projectAction(projectName)
        assembleTestApp(applicationId) // Pass applicationId if provided
        if (isRunningInTeamCity()) {
            createAdbRemoteSession()
        }
        installAndroidTestAPK()
        installTargetAPK(projectName)
        runTestsViaAdb(applicationId) // Pass applicationId to runTestsViaAdb
    }

    // Original method without applicationId
    private fun prepareExecution(
        projectName: String,
        projectPath: String,
        projectAction: (String) -> Unit
    ) {
        copyProject(projectName, projectPath)
        projectAction(projectName)
        assembleTestApp() // No applicationId passed
        if (isRunningInTeamCity()) {
            createAdbRemoteSession()
        }
        installAndroidTestAPK()
        installTargetAPK(projectName)
        runTestsViaAdb()
    }

    internal fun testRunnerStandalone(projectName: String, applicationId: String? = null) =
        prepareExecution(projectName, "../../sources/amper-backend-test/testData/projects/android", {
            assembleTargetAppStandalone(it)
        }, applicationId)

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
        val stdout = ByteArrayOutputStream()
        executeCommand(
            command = listOf(
                "./scripts/espressoSession.sh",
                "-s",
                sessionInfoPath,
                "port"
            ),
            standardOut = stdout
        )

        val output = stdout.toString().trim()
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
        val stdout = ByteArrayOutputStream()
        executeCommand(
            command = listOf(
                "./scripts/espressoSession.sh",
                "-s",
                sessionInfoPath,
                "-n",
                "Amper UI Tests",
                "create"
            ),
            standardOut = stdout
        )
        println(stdout)
    }

    fun deleteAdbRemoteSession() {
        val stdout = ByteArrayOutputStream()
        executeCommand(
            command = listOf(
                "./scripts/espressoSession.sh",
                "-s",
                sessionInfoPath,
                "-n",
                "Amper UI Tests",
                "delete"
            ),
            standardOut = stdout
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

        val stdout = ByteArrayOutputStream()
        val gradlewPath = if (OS.current() == OS.WINDOWS) "../../gradlew.bat" else "../../gradlew"

        // Run the APK build command
        executeCommand(
            command = listOf(
                gradlewPath,
                "-p",
                "../gradle-e2e-test/testData/projects/test-apk",
                "createDebugAndroidTestApk"
            ),
            standardOut = stdout
        )

        println(stdout.toString().trim())

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
            adb("install", apkPath)
        } else {
            throw APKNotFoundException("APK file does not exist at path: $apkPath")
        }
    }

    private fun installTargetAPK(projectName: String) {
        val gradleApkPath = "./tempProjects/$projectName/build/outputs/apk/debug/$projectName-debug.apk"
        val standaloneApkPath =
            "./tempProjects/$projectName/build/tasks/_${projectName}_buildAndroidDebug/gradle-project-debug.apk"
        val gradleApkPathMultiplatform = "./tempProjects/$projectName/android-app/build/outputs/apk/debug/android-app-debug.apk"


        val primaryApkFile = File(gradleApkPath)
        val standaloneApkFile = File(standaloneApkPath)
        val gradleApkPathMultiplatformFile = File(gradleApkPathMultiplatform)


        if (primaryApkFile.exists()) {
            adb("install", gradleApkPath)
        } else if (standaloneApkFile.exists()) {
            adb("install", standaloneApkPath)
        } else if (gradleApkPathMultiplatformFile.exists()) {
            adb("install", gradleApkPathMultiplatform)
        }
        else {
            throw APKNotFoundException("APK file does not exist at paths: $gradleApkPath or $standaloneApkPath or $gradleApkPathMultiplatform")
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

    private fun assembleTargetAppStandalone(projectName: String) {
        val projectDirectory = File("tempProjects" + File.separator + projectName)

        val stdout = ByteArrayOutputStream()

        executeCommand(
            command = listOf(
                "./amper",
                "task",
                ":$projectName:buildAndroidDebug"
            ),
            workingDirectory = projectDirectory,
            standardOut = stdout
        )

        println(stdout.toString().trim())
    }

    class APKNotFoundException(message: String) : Exception(message)



    private fun isEmulatorRunning(): Boolean {
        val stdout = ByteArrayOutputStream()
        executeCommand(
            command = listOf(getAdbPath(), "devices"),
            standardOut = stdout
        )

        val output = stdout.toString().trim()
        return output.contains("emulator")
    }


    private fun getAvailableAvds(): List<String> {
        val avdManagerPath = getAvdManagerPath()
        if (!File(avdManagerPath).exists()) {
            error("avdmanager not found at path: $avdManagerPath")
        }
        val stdout = ByteArrayOutputStream()
        executeCommand(
            command = listOf(avdManagerPath, "list", "avd"),
            standardOut = stdout
        )

        val output = stdout.toString().trim()
        return output.lines()
            .filter { it.contains("Name:") }
            .map { it.split("Name:")[1].trim() }
    }

    private fun getAvdManagerPath(): String {
        val androidHome = System.getenv("ANDROID_HOME") ?: error("ANDROID_HOME is not set")
        val osName = System.getProperty("os.name").toLowerCase()

        val possiblePaths = listOf(
            "$androidHome/cmdline-tools/latest/bin/avdmanager",
            "$androidHome/cmdline-tools/bin/avdmanager",
            "$androidHome/tools/bin/avdmanager"
        )

        val avdManagerPath = possiblePaths.firstOrNull { File(it).exists() }
            ?: error("avdmanager not found in any of the possible locations")

        return if (osName.contains("win")) "$avdManagerPath.bat" else avdManagerPath
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

        var isBootComplete = false
        while (!isBootComplete) {
            val stdout = ByteArrayOutputStream()
            executeCommand(
                command = listOf(getAdbPath(), "shell", "getprop", "sys.boot_completed"),
                standardOut = stdout
            )

            val output = stdout.toString().trim()
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
        return if (System.getProperty("os.name").toLowerCase().contains("win")) {
            "$androidHome\\platform-tools\\adb.exe"
        } else {
            "$androidHome/platform-tools/adb"
        }
    }

    private fun getEmulatorPath(): String {
        val androidHome = System.getenv("ANDROID_HOME") ?: error("ANDROID_HOME is not set")
        return if (System.getProperty("os.name").toLowerCase().contains("win")) {
            "$androidHome\\emulator\\emulator.exe"
        } else {
            "$androidHome/emulator/emulator"
        }
    }
}



