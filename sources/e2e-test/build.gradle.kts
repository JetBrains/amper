/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSetTree.Companion.main
import java.io.ByteArrayOutputStream
import org.jetbrains.amper.core.AmperBuild
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Document
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult
import org.w3c.dom.Element



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
    group = "android_Pure_Emulator_Tests"
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
    group = "android_Pure_Emulator_Tests"
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
    println(cmdOutput)
    println(cmdError)
    if (params.any { it.contains("instrument") }) {
        val outputDir = File(project.projectDir, "androidUITestsAssets/reports")
        if (!outputDir.exists()) {
            outputDir.mkdirs()
        }
         val outputFile = File(outputDir, "report.xml")
         outputFile.writeText(convertToJUnitReport(cmdOutput))
    }
    return stdout
}

    tasks.register("createAndroidRemoteSession") {
        group = "android_Pure_Emulator_Tests"
        doLast { createAdbRemoteSession() }
    }

    tasks.register<Exec>("deleteAndroidRemoteSession") {
        group = "android_Pure_Emulator_Tests"
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



    tasks.register("runTestsViaAdb") {
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
            val apkDirectory =
                project.file("../../examples.pure/android-simple/build/tasks/_android-simple_buildAndroidDebug")

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
                val newText = gradleFileText.replace(
                    "mavenCentral()",
                    """
                mavenCentral()
                mavenLocal()
                maven("https://www.jetbrains.com/intellij-repository/releases")
                maven("https://packages.jetbrains.team/maven/p/ij/intellij-dependencies")
                """.trimIndent()
                )
                if (!gradleFileText.contains("mavenLocal()")) {
                    gradleFile.writeText(newText)
                }

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

        arrayOf("android-simple", "compose-android", "android-appcompat").forEach { dirName ->
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
    }

    tasks.register("installAndroidTestAppForPureTests") {
        group = "android_Pure_Emulator_Tests"
        doLast {
            adb(
                "install", "-t",
                "testData/projects/test-apk/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk"
            )
        }
    }

    tasks.register("uninstallAndroidTestAppForPureTests") {
        group = "android_Pure_Emulator_Tests"
        doLast {
            adb(
                "uninstall",
                "com.jetbrains.sample.app.test"
            )
        }
    }

    tasks.register("uninstallAndroidAppForPureTests") {
        group = "android_Pure_Emulator_Tests"
        doLast {
            adb(
                "uninstall",
                "com.jetbrains.sample.app"
            )
        }
    }

    tasks.register("runTestsForPureAmper") {
        group = "android_Pure_Emulator_Tests"

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
                "-t",
                "com.jetbrains.sample.app.test/androidx.test.runner.AndroidJUnitRunner",
            )
        }
    }

    fun convertToJUnitReport(instrumentationOutput: String): String {
        val lines = instrumentationOutput.lines()
        val testCases = mutableListOf<String>()
        var className = ""
        var testName = ""
        var time = 0.0

        lines.forEach { line ->
            when {
                line.startsWith("INSTRUMENTATION_STATUS: class=") -> {
                    className = line.substringAfter("INSTRUMENTATION_STATUS: class=")
                }

                line.startsWith("INSTRUMENTATION_STATUS: test=") -> {
                    testName = line.substringAfter("INSTRUMENTATION_STATUS: test=")
                }

                line.startsWith("INSTRUMENTATION_STATUS_CODE: ") -> {
                    val statusCode = line.substringAfter("INSTRUMENTATION_STATUS_CODE: ").toInt()
                    if (statusCode == 0) {
                        testCases.add("""<testcase classname="$className" name="$testName" time="$time" />""")
                    }
                }

                line.startsWith("Time: ") -> {
                    time = line.substringAfter("Time: ").toDouble()
                }
            }
        }

        return """
        <?xml version="1.0" encoding="UTF-8"?>
        <testsuite name="AndroidJUnitRunner" tests="${testCases.size}" failures="0" time="$time">
            ${testCases.joinToString("\n")}
        </testsuite>
    """.trimIndent()
    }

tasks.register("installAndTestPureApps") {
    group = "android_Pure_Emulator_Tests"

    doLast {
        val rootDirectory = project.file("../../androidTestProjects")
        val projects = rootDirectory.listFiles()?.filter { it.isDirectory } ?: throw GradleException("No projects found in $rootDirectory.")

        // Ensure there are projects to process.
        if (projects.isEmpty()) {
            throw GradleException("No projects found in $rootDirectory.")
        }

        projects.forEach { projectDir ->
            println("Processing project in directory: ${projectDir.name}")
            val apkDirectory = File(projectDir, "build/tasks/_${projectDir.name}_buildAndroidDebug/")

            val apkFiles = apkDirectory.listFiles { _, name -> name.endsWith(".apk") } ?: emptyArray()

            if (apkFiles.isNotEmpty()) {
                val apkFile = apkFiles.first()
                println("Installing APK: ${apkFile.name}")
                installAndRunApk(apkFile)
                //updateClassnameAndRenameFile(projectDir.name)

            } else {
                throw GradleException("No APK files found in $apkDirectory.")
            }
        }
        val outputDir = File(project.projectDir, "androidUITestsAssets/reports")
        combineJUnitReports(outputDir.absolutePath,"main.xml")
    }
}

// Helper function to install and run an APK file.
fun installAndRunApk(apkFile: File) {
    val packageName = "com.jetbrains.sample.app"

    adb("install", "-t", apkFile.absolutePath)
    adb("shell", "am", "instrument", "-w", "-r", "$packageName.test/androidx.test.runner.AndroidJUnitRunner")
    println("Uninstalling $packageName")
    adb("uninstall", packageName)
}

fun updateClassnameAndRenameFile(newClassName: String) {
    val outputDir = File(project.projectDir, "androidUITestsAssets/reports")
    val file = File(outputDir, "report.xml")
    val dbFactory = DocumentBuilderFactory.newInstance()
    val dBuilder = dbFactory.newDocumentBuilder()
    val doc: Document = dBuilder.parse(file)
    doc.documentElement.normalize()

    val testcase = doc.getElementsByTagName("testcase").item(0)
    testcase.attributes.getNamedItem("classname").textContent = newClassName

    val transformerFactory = javax.xml.transform.TransformerFactory.newInstance()
    val transformer = transformerFactory.newTransformer()
    val source = javax.xml.transform.dom.DOMSource(doc)
    val tempFile = File(file.parent, "temp_${file.name}")
    val result = javax.xml.transform.stream.StreamResult(tempFile)
    transformer.transform(source, result)

    val backupFile = File(file.parent, "${file.name}.backup")
    file.renameTo(backupFile)

    val newFileName = newClassName.substringAfterLast('.') + ".xml"
    val newFile = File(file.parent, newFileName)
    tempFile.renameTo(newFile)

    println("File has been updated and renamed to $newFileName")
}

fun combineJUnitReports(folderPath: String, outputFileName: String) {
    println("Starting to combine JUnit reports from folder: $folderPath")

    val dbFactory = DocumentBuilderFactory.newInstance()
    val dBuilder = dbFactory.newDocumentBuilder()
    val outputDoc: Document = dBuilder.newDocument()
    val rootElement: Element = outputDoc.createElement("testsuites")
    outputDoc.appendChild(rootElement)

    File(folderPath).listFiles { _, name -> name.endsWith(".xml") }?.forEach { file ->
        println("Processing file: ${file.absolutePath}")
        val doc = dBuilder.parse(file)
        doc.documentElement.normalize()
        val nodeList = doc.documentElement.childNodes
        for (i in 0 until nodeList.length) {
            val node = nodeList.item(i)
            if (node.nodeType == Element.ELEMENT_NODE) {
                // Import node to the output document
                val importedNode = outputDoc.importNode(node, true)
                rootElement.appendChild(importedNode)
            }
        }
    }

    println("All files processed. Generating combined report...")

    // Define the output file in the same directory as the input files
    val outputPath = "$folderPath/$outputFileName"
    val resultFile = File(outputPath)

    // Write the content into xml file
    val transformerFactory = TransformerFactory.newInstance()
    val transformer = transformerFactory.newTransformer()
    val source = DOMSource(outputDoc)
    val result = StreamResult(resultFile)
    transformer.transform(source, result)

    println("Combined report generated successfully: ${resultFile.absolutePath}")
}

val assemblePureTestAPK by tasks.registering(Exec::class) {
    group = "android_Pure_Emulator_Tests"
    workingDir = file("testData/projects/test-apk/app")
    commandLine("gradle", "createDebugAndroidTestApk")
}

tasks.register("installAndroidTestApp") {
    doLast {
        adb(
            "install",
            "testData/projects/compose-android-ui/build/outputs/apk/androidTest/debug/compose-android-ui-debug-androidTest.apk"
        )
    }
}

tasks.register("installAndroidTestPureTest") {
    doLast {
        adb(
            "install",
            "testData/projects/test-apk/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk"
        )
    }
}

