import java.io.BufferedReader
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStreamReader

/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

open class AndroidBaseTest():TestBase() {
    val sessionInfoPath = "./scripts/device.session.json"

    private fun prepareExecution(projectName: String, projectPath: String, projectAction: (String) -> Unit) {
        copyProject(projectName, projectPath)
        projectAction(projectName)
        assembleTestApp()
        if (isRunningInTeamCity()) {
            createAdbRemoteSession()
        }
        installAndroidTestAPK()
        installTargetAPK(projectName)
        runTestsViaAdb()
    }

    // TO DO ask about changing example structure for using real examples not test data stuff
    internal fun testRunnerGradle(projectName: String) =
        prepareExecution(projectName, "../../sources/gradle-e2e-test/testData/projects") {
            prepareProjectsAndroidForGradle(it)
        }

    internal fun testRunnerStandalone(projectName: String) =
        prepareExecution(projectName, "../../sources/amper-backend-test/testData/projects/android") {
            assembleTargetAppStandalone(it)
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
            throw RuntimeException("Session wasn't created!")
        }
        return output
    }

    private fun adb(vararg params: String): ByteArrayOutputStream {
        val stdout = ByteArrayOutputStream()
        val stderr = ByteArrayOutputStream()
        val cmd = mutableListOf<String>()

        if (!isRunningInTeamCity()) {
            // Для локального запуска
            if (!isEmulatorRunning()) {
                startEmulator()
            }
            cmd.add("${System.getenv("ANDROID_HOME")}/platform-tools/adb")
        } else {
            // Для CI (TeamCity)
            val adbCompanion = getAdbRemoteSession()
            val host = adbCompanion.split(':')
            cmd.apply {
                add("${System.getenv("ANDROID_HOME")}/platform-tools/adb")
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

    private fun assembleTestApp() {
        val stdout = ByteArrayOutputStream()

        executeCommand(
            command = listOf(
                "../../gradlew",
                "-p",
                "../gradle-e2e-test/testData/projects/test-apk",
                "createDebugAndroidTestApk"
            ),
            standardOut = stdout
        )

        println(stdout.toString().trim())
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

        val primaryApkFile = File(gradleApkPath)
        val standaloneApkFile = File(standaloneApkPath)

        if (primaryApkFile.exists()) {
            adb("install", gradleApkPath)
        } else if (standaloneApkFile.exists()) {
            adb("install", standaloneApkPath)
        } else {
            throw APKNotFoundException("APK file does not exist at paths: $gradleApkPath or $standaloneApkPath")
        }
    }

    private fun runTestsViaAdb() {
        adb("shell", "settings", "put", "global", "window_animation_scale", "0.0")
        adb("shell", "settings", "put", "global", "transition_animation_scale", "0.0")
        adb("shell", "settings", "put", "global", "animator_duration_scale", "0.0")
        adb("shell", "settings", "put", "secure", "long_press_timeout", "1000")

        val output = adb(
            "shell",
            "am",
            "instrument",
            "-w",
            "-r",
            "com.jetbrains.sample.app.test/androidx.test.runner.AndroidJUnitRunner"
        ).toString(Charsets.UTF_8)

        if (!output.contains("OK (1 test)") || output.contains("Error")) {
            throw RuntimeException("Test failed with output:\n$output")
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
            command = listOf("${System.getenv("ANDROID_HOME")}/platform-tools/adb", "devices"),
            standardOut = stdout
        )

        val output = stdout.toString()
        return output.contains("emulator-")
    }

    private fun getAvailableAvds(): List<String> {
        val stdout = ByteArrayOutputStream()
        executeCommand(
            command = listOf("${System.getenv("ANDROID_HOME")}/emulator/emulator", "-list-avds"),
            standardOut = stdout
        )

        val output = stdout.toString().trim()
        println("Available AVDs raw output: $output")  // Логирование необработанного списка AVD

        // Извлекаем строки, представляющие имена AVD, игнорируя лишний вывод
        val avdList = output.lines().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("INFO") }
        println("Filtered AVDs: $avdList")  // Логирование отфильтрованного списка AVD

        return avdList
    }

    private fun startEmulator() {
        val emulatorPath = "${System.getenv("ANDROID_HOME")}/emulator/emulator"
        val availableAvds = getAvailableAvds()

        if (availableAvds.isEmpty()) {
            throw RuntimeException("No AVDs available. Please create at least one AVD.")
        }

        val avdName = availableAvds[0] // Берем первый доступный AVD
        println("Запуск эмулятора $avdName...")

        val processBuilder = ProcessBuilder(emulatorPath, "-avd", avdName)
        processBuilder.redirectErrorStream(true)
        val process = processBuilder.start()

        // Ждем, пока эмулятор полностью запустится
        var isBootComplete = false
        while (!isBootComplete) {
            val stdout = ByteArrayOutputStream()
            executeCommand(
                command = listOf("${System.getenv("ANDROID_HOME")}/platform-tools/adb", "shell", "getprop", "sys.boot_completed"),
                standardOut = stdout
            )

            val output = stdout.toString().trim()
            if (output == "1") {
                isBootComplete = true
            } else {
                println("Ожидание запуска эмулятора...")
                Thread.sleep(5000)
            }
        }

        println("Эмулятор запущен.")
    }
}



