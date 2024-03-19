/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */
import java.io.ByteArrayOutputStream


val sessionInfoPath by extra { "$buildDir/device.session.json" }


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
    }
    val cmdOutput = stdout.toString()
    print(cmdOutput)
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

tasks.register("installAndroidTestApp") {
    doLast {
        adb(
            "install",
            "testData/projects/compose-android-ui/build/outputs/apk/androidTest/debug/compose-android-ui-debug-androidTest.apk"
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
val assembleDebugAndTestAPK by tasks.registering(Exec::class) {
    workingDir = file("testData/projects/compose-android-ui")
    commandLine("./gradlew", "assembleDebugAndTest")
}
