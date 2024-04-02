/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSetTree.Companion.main
import java.io.ByteArrayOutputStream
import org.jetbrains.amper.core.AmperBuild



val sessionInfoPath by extra { "$buildDir/device.session.json" }

val amperBuildNumber = AmperBuild.BuildNumber // Assume AmperBuild.BuildNumber is accessible and holds a value



tasks.withType<Test>().configureEach {
    if (name != "runEmulatorTests") {
        systemProperties["junit.jupiter.execution.parallel.enabled"] = true
        systemProperties["junit.jupiter.execution.parallel.mode.default"] = "concurrent"

        for (task in rootProject.getTasksByName("publishToMavenLocal", true)) {
            dependsOn(task)
        }

        useJUnitPlatform()

        val inBootstrapMode: String? by project
        inBootstrapMode?.let {
            if (it == "true") {
                filter {
                    excludeTestsMatching("*BootstrapTest*")
                }
            }
        }
        maxHeapSize = "4g"

        exclude { include ->
            include.file.name.contains("EmulatorTests")
        }
    }
}



fun getOrCreateAdbRemoteSession(): String {
    val stdout = ByteArrayOutputStream()
    var adbCompanion = ""
    project.exec {
        commandLine = listOf("./scripts/espressoSession.sh", "-s", sessionInfoPath, "-n", "Amper UI Tests", "create")
        standardOutput = stdout
    }
    println(stdout)
    stdout.toString().lines().forEach {
        println(it)
        if (it.startsWith("ADB_COMPANION")) {
            adbCompanion = it.split('=')[1]
        }
    }
    return adbCompanion
}

tasks.register("createAndConnectAndroidRemoteSession") {
    doLast {
        val adbCompanion = getOrCreateAdbRemoteSession()
    }
}

fun createAdbRemoteSession() {
    val stdout = ByteArrayOutputStream()
    project.exec {
        commandLine = listOf(
            "./scripts/espressoSession.sh",
            "-s",
            sessionInfoPath,
            "-n",
            "Amper UI Tests",
            "create"
        )
        standardOutput = stdout
    }
    println(stdout)
}

fun getAdbRemoteSession(): String {
    val stdout = ByteArrayOutputStream()
    project.exec {
        commandLine = listOf(
            "./scripts/espressoSession.sh",
            "-s",
            sessionInfoPath,
            "port"
        )
        standardOutput = stdout
    }
    println(stdout)
    val output = stdout.toString().trim()
    if (output.isEmpty()) {
        throw GradleException("Session wasn't created!")
    }
    return output
}

fun adb(vararg params: String): ByteArrayOutputStream {
    val stdout = ByteArrayOutputStream()
    val adbCompanion = getAdbRemoteSession()
    val stderr = ByteArrayOutputStream()
    val host = adbCompanion.split(':')
    val cmd = mutableListOf<String>().apply {
        add("${System.getenv("ANDROID_HOME")}/platform-tools/adb")
        add("-H")
        add(host[0])
        add("-P")
        add(host[1])
        // add("-s", "emulator-5560") // Uncomment this line if needed
        addAll(params)
    }
    println("Executing adb: $cmd")
    project.exec {
        commandLine = cmd
        standardOutput = stdout
        errorOutput = stderr
    }
    val cmdOutput = stdout.toString()
    val cmdError = stderr.toString()
    if (cmdError.contains("Error: Activity class") || cmdOutput.contains("Error: Activity class")) {
        throw RuntimeException("Error launching MainActivity. Failing the task.")
    }
    println(cmdOutput)
    return stdout
}

tasks.register("createAndroidRemoteSession") {
    doLast { createAdbRemoteSession() }
}

tasks.register<Exec>("deleteAndroidRemoteSession") {
    commandLine = listOf("./scripts/espressoSession.sh", "-s", sessionInfoPath, "delete")
}

tasks.register("getEmulators") {
    doLast {
        val t = adb("devices")
            .toString().lines().filter { it.contains("emulator") }
            .forEach { println(it.trim().split("\\s+").first()) }
    }
}

tasks.register("uninstallPackages") {
    dependsOn("createAndroidRemoteSession")
    doLast {
        adb("shell", "pm", "list", "packages", "-3")
            .toString().lines().filter { it.contains("jetbrains") }
            .forEach {
                adb("uninstall", it.replace("package:", "")).toString()
            }
    }
    mustRunAfter("createAndroidRemoteSession")
}

tasks.register("installDebugApp") {
    doLast {
        adb(
            "install",
            "testData/projects/compose-android-ui/build/outputs/apk/debug/compose-android-ui-debug.apk"
        )
    }
}



tasks.register("runTestsViaAdb"){
    dependsOn("installDebugApp")
    dependsOn("installAndroidTestApp")
    doFirst {

        adb("shell", "settings", "put", "global", "window_animation_scale", "0.0")
        adb("shell", "settings", "put", "global", "transition_animation_scale", "0.0")
        adb("shell", "settings", "put", "global", "animator_duration_scale", "0.0")
        adb("shell", "settings", "put", "secure", "long_press_timeout", "1000")

        adb(
            "shell",
            "am",
            "instrument",
            "-w",
            "-r",
            "-e",
            "class",
            "com.jetbrains.compose_android_ui_test.InstrumentedTests",
            "com.jetbrains.compose_android_ui_test.test/androidx.test.runner.AndroidJUnitRunner"
        )
    }
}

tasks.register("InstallPureAPKSampleApp") {
    doLast {
        val apkDirectory = project.file("../../examples.pure/android-simple/build/tasks/_android-simple_buildAndroidDebug")

        val apkFiles = apkDirectory.listFiles { dir, name ->
            println(name)
            name.endsWith("-debug.apk")
        } ?: arrayOf()


        if (apkFiles.isNotEmpty()) {
            val apkFile = apkFiles.first()
            adb("install", apkFile.absolutePath)
        } else {
            throw GradleException("No APK file matching the pattern '-debug.apk' was found in $apkDirectory")
        }
    }
}

tasks.register("runPureSampleAPK"){
    dependsOn("InstallPureAPKSampleApp")
    doFirst {

        adb("shell", "settings", "put", "global", "window_animation_scale", "0.0")
        adb("shell", "settings", "put", "global", "transition_animation_scale", "0.0")
        adb("shell", "settings", "put", "global", "animator_duration_scale", "0.0")
        adb("shell", "settings", "put", "secure", "long_press_timeout", "1000")

        adb(
            "shell",
            "am",
            "start",
            "-n",
            "com.jetbrains.sample.app/com.jetbrains.sample.app.MainActivity"
        )
    }
}
val assembleDebugAndTestAPK by tasks.registering(Exec::class) {
    workingDir = file("testData/projects/compose-android-ui")
    commandLine("gradle", "assembleDebugAndTest")
}

tasks.register("installAndroidTestApp") {
    doLast {
        adb(
            "install",
            "testData/projects/compose-android-ui/build/outputs/apk/androidTest/debug/compose-android-ui-debug-androidTest.apk"
        )
    }
}

val prepareProjects = tasks.register("prepareProjectsAndroid") {
    doLast {
        val projectName: String = "compose-android-ui"
        val runWithPluginClasspath: Boolean = true
        val pathToProjects: String = "testData/projects/compose-android-ui"

        val implementationDir = file("../../sources").absoluteFile
        val originalDir = file(pathToProjects).absoluteFile

        require(implementationDir.exists()) { "Amper plugin project not found at $implementationDir" }
        require(originalDir.exists()) { "Test project not found at $originalDir" }

        val gradleFile = originalDir.resolve("settings.gradle.kts")
        require(gradleFile.exists()) { "file not found: $gradleFile" }

        if (runWithPluginClasspath) {
            val lines = gradleFile.readLines().filterNot { "<REMOVE_LINE_IF_RUN_WITH_PLUGIN_CLASSPATH>" in it }
            gradleFile.writeText(lines.joinToString("\n"))

            val gradleFileText = gradleFile.readText()


            // Replace mavenCentral with additional repositories
            val newText = gradleFileText.replace("mavenCentral()",
                """
                mavenCentral()
                mavenLocal()
                maven("https://www.jetbrains.com/intellij-repository/releases")
                maven("https://packages.jetbrains.team/maven/p/ij/intellij-dependencies")
                """.trimIndent()
            )
            if (!gradleFileText.contains("mavenLocal()")) { gradleFile.writeText(newText)}

            require(gradleFile.readText().contains("mavenLocal")) {
                "Gradle file must have 'mavenLocal' after replacement: $gradleFile"
            }

            // Dynamically add Amper plugin version
            val updatedText = gradleFile.readText().replace(
                "id(\"org.jetbrains.amper.settings.plugin\")",
                "id(\"org.jetbrains.amper.settings.plugin\") version(\"${"+"}\")"
            )
            if (!gradleFileText.contains(amperBuildNumber)) {
                gradleFile.writeText(updatedText)
            }


            require(gradleFile.readText().contains("version(")) {
                "Gradle file must have 'version(' after replacement: $gradleFile"
            }
        }

        if (gradleFile.readText().contains("includeBuild(\".\"")) {
            throw GradleException("Example project $projectName has a relative includeBuild() call, but it's run within Amper tests from a moved directory. Add a comment '<REMOVE_LINE_IF_RUN_WITH_PLUGIN_CLASSPATH>' on the same line if this included build is for Amper itself (will be removed if Amper is on the classpath).")
        }
    }
}

tasks.register<Copy>("copyAndroidTestProjects") {
    group = "android_Pure_Emulator_Tests"
    into(project.file("../../androidTestProjects"))

    arrayOf("android-aar", "android-simple", "compose-android", "android-appcompat").forEach { dirName ->
        val sourcePath = "../../examples.pure/$dirName"
        from(sourcePath) {
            into(dirName)
        }
    }
}

tasks.register("cleanAndroidTestProjects") {
    group = "android_Pure_Emulator_Tests"
    doLast {
        val folderPath = project.file("../../androidTestProjects")

        if (folderPath.exists()) {
            folderPath.listFiles()?.forEach { file ->
                delete(file)
            }
        }
    }
}


tasks.register("build_apks_for_ui_tests") {
    group = "android_Pure_Emulator_Tests"

    doLast {
        val basePath = project.file("../../androidTestProjects")

        if (basePath.exists() && basePath.isDirectory) {
            basePath.listFiles { file -> file.isDirectory }?.forEach { dir ->
                val command = " bash ./amper.sh task :${dir.name}:buildAndroidDebug"

                project.exec {
                    workingDir(dir)
                    commandLine("bash", "-c", command)

                }
            }
        } else {
            println("The path '$basePath' does not exist or is not a directory.")
        }
    }
    finalizedBy("copyAndroidTestProjects")
}

tasks.register("installAndroidTestAppForPureTests") {
    group = "android_Pure_Emulator_Tests"
    doLast {
        adb(
            "install",
            "androidTestAssets/app-debug-androidTest.apk"
        )
    }
}

//TEMPORARY FOR FAST CHECK WILL BE REFACTOR SOON BECAUSE WE WILL BE RUN APPS OTHER WAY
tasks.register("installAndRunPureApps") {

    group = "android_Pure_Emulator_Tests"
    doLast {
        val rootDirectory = project.file("../../androidTestProjects")
        if (rootDirectory.listFiles() == null || rootDirectory.listFiles()!!.isEmpty()) {
            throw GradleException("No projects was found in $rootDirectory")
        }

        rootDirectory.listFiles()?.filter { it.isDirectory }?.forEach { projectDir ->
            println("Processing project in directory: ${projectDir.name}")

            val apkDirectory = File(projectDir, "build/tasks/_${projectDir.name}_buildAndroidDebug/")
            println(projectDir.listFiles())

            val apkFiles = apkDirectory.listFiles { _, name ->
                name.endsWith(".apk")
            } ?: arrayOf()

            if (apkFiles.isNotEmpty()) {

                val apkFile = apkFiles.first()
                println("Installing APK: ${apkFile.name}")

                adb("install", apkFile.absolutePath)
                val packageName = "com.jetbrains.sample.app"

                println("Running task: runPureSampleAPK")
                val runPureSampleAPKTask = project.tasks.findByName("runPureSampleAPK")
                runPureSampleAPKTask?.actions?.forEach { action ->
                    action.execute(runPureSampleAPKTask)
                }

                println("Uninstalling $packageName")
                adb("uninstall", packageName)
            } else {
                throw GradleException("No APK file matching the pattern '-debug.apk' was found in $apkDirectory ${apkDirectory.listFiles()}")
            }
        }
    }
}

tasks.register("TestFailTask") {
    group = "android_Pure_Emulator_Tests"

    doLast {
        throw GradleException("Forced failure.")
    }
}



