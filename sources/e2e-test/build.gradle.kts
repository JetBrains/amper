/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */
import org.jetbrains.amper.core.AmperBuild
import org.w3c.dom.Document
import org.w3c.dom.Element
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.nio.charset.StandardCharsets
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult



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
            project.file("../amper-backend-test/testData/projects/android/simple/build/tasks/_simple_buildAndroidDebug")

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

    arrayOf("simple", "appcompat").forEach { dirName ->
        val sourcePath = "../amper-backend-test/testData/projects/android/$dirName"
        from(sourcePath) {
            into(dirName)
        }
    }

    arrayOf("compose-android").forEach { dirName ->
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

tasks.register("installAndroidTestAppForPureTestsDebug") {
    group = "android_Pure_Emulator_Tests"
    doLast {
        adb(
            "install", "-t",
            "testData/projects/test-apk/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk"
        )
    }
}

fun convertToJUnitReport(instrumentationOutput: String): String {
    val lines = instrumentationOutput.lines()
    val testCases = mutableListOf<String>()
    var className = ""
    var testName = ""
    var failureMessage = StringBuilder()
    var inFailureBlock = false
    var testFailed = false
    var time = 0.0

    lines.forEach { line ->
        when {
            line.startsWith("INSTRUMENTATION_STATUS: class=") -> className =
                line.substringAfter("INSTRUMENTATION_STATUS: class=")

            line.startsWith("INSTRUMENTATION_STATUS: test=") -> {
                // If we're starting a new test, add the previous one to the list
                if (testName.isNotEmpty() && className.isNotEmpty()) {
                    val testCase = if (testFailed) {
                        """<testcase classname="$className" name="$testName" time="$time">
                            |<failure><![CDATA[$failureMessage]]></failure>
                            |</testcase>""".trimMargin()
                    } else {
                        """<testcase classname="$className" name="$testName" time="$time" />"""
                    }
                    testCases.add(testCase)
                    // Reset for the next test
                    failureMessage.clear()
                    testFailed = false
                }
                testName = line.substringAfter("INSTRUMENTATION_STATUS: test=")
            }

            line.startsWith("INSTRUMENTATION_STATUS_CODE: ") -> {
                val statusCode = line.substringAfter("INSTRUMENTATION_STATUS_CODE: ").toInt()
                inFailureBlock = statusCode == -2
                testFailed = testFailed || inFailureBlock
            }

            line.startsWith("Time: ") -> time = line.substringAfter("Time: ").toDouble()
            line.startsWith("There was 1 failure:") -> inFailureBlock = true
            line.startsWith("INSTRUMENTATION_CODE: ") -> inFailureBlock = false // End of failures block
            inFailureBlock -> failureMessage.appendLine(line)
        }
    }

    // Add the last test case if it exists
    if (testName.isNotEmpty() && className.isNotEmpty()) {
        val testCase = if (testFailed) {
            """<testcase classname="$className" name="$testName" time="$time">
                |<failure><![CDATA[$failureMessage]]></failure>
                |</testcase>""".trimMargin()
        } else {
            """<testcase classname="$className" name="$testName" time="$time" />"""
        }
        testCases.add(testCase)
    }

    return """
        |<?xml version="1.0" encoding="UTF-8"?>
        |<testsuite name="InstrumentationTestRunner" tests="${testCases.size}" failures="${
        testCases.count {
            it.contains(
                "<failure"
            )
        }
    }" time="$time">
        |${testCases.joinToString("\n")}
        |</testsuite>
        """.trimMargin()
}

tasks.register("installAndTestPureApps") {
    group = "android_Pure_Emulator_Tests"

    doLast {
        val rootDirectory = project.file("../../androidTestProjects")
        val projects = rootDirectory.listFiles()?.filter { it.isDirectory }
            ?: throw GradleException("No projects found in $rootDirectory.")

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
                updateClassnameAndRenameFile(projectDir.name)

            } else {
                throw GradleException("No APK files found in $apkDirectory.")
            }
        }
        val outputDir = File(project.projectDir, "androidUITestsAssets/reports")
        combineJUnitReports(outputDir.absolutePath, "main.xml")
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
    if (!file.exists()) {
        println("Report file does not exist.")
        return
    }

    val dbFactory = DocumentBuilderFactory.newInstance()
    val dBuilder = dbFactory.newDocumentBuilder()
    val doc: Document = dBuilder.parse(file)
    doc.documentElement.normalize()

    val testcases = doc.getElementsByTagName("testcase")
    for (i in 0 until testcases.length) {
        val testcase = testcases.item(i)
        testcase.attributes.getNamedItem("classname").textContent = newClassName
    }

    val transformerFactory = TransformerFactory.newInstance()
    val transformer = transformerFactory.newTransformer()
    val source = DOMSource(doc)
    val tempFile = File(file.parent, "temp_${file.name}")
    val result = StreamResult(tempFile)
    transformer.transform(source, result)

    val backupFile = File(file.parent, "${file.name}.backup")
    if (backupFile.exists()) {
        // Optional: Handle or log if a backup already exists to avoid data loss.
    }
    val success = file.renameTo(backupFile)
    if (!success) {
        println("Failed to create backup of the original file.")
        return
    }

    val newFileName = newClassName.substringAfterLast('.') + ".xml"
    val newFile = File(file.parent, newFileName)
    val renameSuccess = tempFile.renameTo(newFile)
    if (!renameSuccess) {
        println("Failed to rename temporary file to $newFileName")
        // Optional: handle this case, maybe attempt to restore from backup
    } else {
        println("File has been updated and renamed to $newFileName")
    }
}


fun combineJUnitReports(folderPath: String, outputFileName: String) {
    println("Starting to combine JUnit reports from folder: $folderPath")

    val dbFactory = DocumentBuilderFactory.newInstance()
    val dBuilder = dbFactory.newDocumentBuilder()
    val outputDoc: Document = dBuilder.newDocument()
    val rootElement: Element = outputDoc.createElement("testsuite")
    outputDoc.appendChild(rootElement)

    var totalFailures = 0
    var totalTests = 0
    var totalTime = 0.0

    File(folderPath).listFiles { _, name -> name.endsWith(".xml") }?.forEach { file ->
        println("Processing file: ${file.absolutePath}")
        val doc = dBuilder.parse(file)
        doc.documentElement.normalize()

        val nodeList = doc.getElementsByTagName("testcase")
        for (i in 0 until nodeList.length) {
            val node = nodeList.item(i) as Element
            val time = node.getAttribute("time").toDouble()
            if (time > 0.0) {
                // Only increment totalTests and import node if time > 0.0
                totalTests++
                val importedNode = outputDoc.importNode(node, true)
                rootElement.appendChild(importedNode)
            }
        }

        val testsuite = doc.getElementsByTagName("testsuite").item(0) as Element
        totalFailures += testsuite.getAttribute("failures").toInt()
        totalTime += testsuite.getAttribute("time").toDouble()
    }

    // Set aggregated attributes for the testsuite element
    rootElement.setAttribute("name", "InstrumentationTestRunner")
    rootElement.setAttribute("tests", totalTests.toString())
    rootElement.setAttribute("failures", totalFailures.toString())
    rootElement.setAttribute("time", String.format("%.3f", totalTime))

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

//region iOS Tasks
val spaceAppBundleId = "com.jetbrains.circlet.nightly"
val testHostAppBundleId = "com.jetbrains.space.ios.CircletEAPUITests.xctrunner"
val xctestBundleId = "com.jetbrains.space.ios.CircletEAPUITests"

fun getOrCreateRemoteSession(): String {
    var idbCompanion = if (project.hasProperty("idbCompanion")) project.property("idbCompanion") as String else ""
    if (idbCompanion == "") {
        val stdout = ByteArrayOutputStream()
        project.exec {
            commandLine("./scripts/session.sh", "-s", sessionInfoPath, "-n", "Amper UI Test", "create")
            standardOutput = stdout
        }
        stdout.toString().lines().forEach {
            println(it)
            if (it.startsWith("IDB_COMPANION")) {
                idbCompanion = it.split('=')[1]
            }
        }
    }
    return idbCompanion
}

fun stringToByteArrayOutputStream(inputString: String): ByteArrayOutputStream {
    val outputStream = ByteArrayOutputStream()
    outputStream.write(inputString.toByteArray(StandardCharsets.UTF_8))
    return outputStream
}

fun idb(outputStream: OutputStream? = null, vararg params: String): String {
    val stdout = outputStream ?: ByteArrayOutputStream()
    val idbCompanion = getOrCreateRemoteSession() // Assuming this function is defined elsewhere
    val cmd = listOf("idb", *params)
    println("Executing IDB: $cmd")

    project.exec {
        commandLine = cmd
        environment("IDB_COMPANION", idbCompanion)
        standardOutput = stdout
    }

    val cmdOutput = stdout.toString()
    if (outputStream == null) {
        print(cmdOutput)
    }

    return cmdOutput
}


tasks.register("createRemoteSessionIOS") {
    group = "ios tests"

    doLast {
        getOrCreateRemoteSession()
    }
}

tasks.register<Exec>("deleteRemoteSessionIOS") {
    group = "ios tests"
    commandLine("./scripts/session.sh", "-s", sessionInfoPath, "delete")
}



tasks.register<Exec>("buildIOSApp") {
    group = "ios tests"
    // Task group and description for better organization and visibility
    group = "ios tests"
    description = "Build the iOS app using xcodebuild"
    workingDir = file("testData/projects/compose-ios-project-ui/iosApp")


    // Command to execute
    commandLine("sh", "-c", """
        xcodebuild -scheme iosApp \
                   -project iosApp.xcodeproj \
                   -configuration Debug \
                   -sdk iphonesimulator \
                   BUILD_DIR='${workingDir.path+"/AppBundle"}' \
                   build
    """.trimIndent())


    // Log output to standard out and error
    standardOutput = System.out
    errorOutput = System.err

    // Actions to perform before the task execution
    doFirst {
        println("Starting to build iOS app...")
    }

    // Actions to perform after the task execution
    doLast {
        println("Finished building iOS app.")
    }
}


tasks.register<Exec>("buildIOSAppUITests") {
    // Task group and description for better organization and visibility
    group = "ios tests"
    description = "Build the iOS app UI tests bundle using xcodebuild"
    workingDir = file("testData/projects/compose-ios-project-ui/iosApp")


    // Command to execute
    commandLine("sh", "-c", """
        xcodebuild -scheme iosAppUITests \
                   -project iosApp.xcodeproj \
                   -configuration Debug \
                   -sdk iphonesimulator \
                   BUILD_DIR='${workingDir.path+"/TestBundle"}' \
                   build-for-testing
    """.trimIndent())



    // Log output to standard out and error
    standardOutput = System.out
    errorOutput = System.err

    // Actions to perform before the task execution
    doFirst {
        println("Starting to build iOS app UI tests bundle...")
    }
}

tasks.register("installAppBundle") {
    group = "ios tests"
    doLast {
        var workingDir = file("testData/projects/compose-ios-project-ui/iosApp")

        val appsBuildDir = "/Users/Vladimir.Naumenko/Desktop/iOS-tests/amper/sources/e2e-test/testData/tempIOSGradleTests/ios-app/build/apple/bin/Debug-iphonesimulator/"
        val appArtifactName = "iosApp.app"
        idb(params = arrayOf("install",appsBuildDir+appArtifactName))

    }
}

tasks.register("installBundleAppUITests") {
    group = "ios tests"
    doLast {
        var workingDir = file("testData/iOSTestsAssets/")

        val appArtifactNameFullName = "iosAppUITests-Runner.app"
        idb(params = arrayOf("install", "/Users/Vladimir.Naumenko/Desktop/iOS-tests/amper/sources/e2e-test/testData/iOSTestsAssets/iosAppUITests-Runner.app"))
        idb(params = arrayOf("xctest","install", "/Users/Vladimir.Naumenko/Desktop/iOS-tests/amper/sources/e2e-test/testData/iOSTestsAssets/iosAppUITests-Runner.app/Plugins/iosAppUITests.xctest"))


    }
}

tasks.register("checkTestList") {
    group = "ios tests"
    doLast {
        idb(params = arrayOf("xctest","list"))

    }
}

tasks.register("showInstalledApps") {
    group = "ios tests"
    doLast {
        idb(params = arrayOf("list-apps"))

    }
}

tasks.register("getSimulators") {
    group = "ios tests"
    doLast {
        idb(params = arrayOf("list-targets"))

    }
}



tasks.register("runUITests") {
    val appBundleId = "iosApp.iosApp"
    val testHostAppBundleId = "iosApp.iosAppUITests.xctrunner"
    val xctestBundleId = "iosApp.iosAppUITests"
    val xcTestLogsDir = "$buildDir/reports/xctest"
    group = "ios tests"
    doLast {
        idb(
            params = arrayOf(
                "--log", "DEBUG",
                "xctest",
                "run",
                "ui",
                xctestBundleId,
                appBundleId,
                testHostAppBundleId

            )
        )
    }
}

fun Project.checkCopySuccess(dirNames: List<String>, basePath: String) {
    dirNames.forEach { dirName ->
        val destPath = file("$basePath/$dirName")
        if (!destPath.exists()) {
            throw GradleException("Failed to copy $dirName to $destPath")
        }
    }
}

tasks.register<Copy>("copyiOSTestProjects") {
    group = "ios tests"
    val destinationBasePath = "testdata/tempIOSGradleTests/"
    into(project.file(destinationBasePath))

    val directories = arrayOf("ios-app", "multiplatform-lib-ios-framework")
    directories.forEach { dirName ->
        val sourcePath = "testdata/projects/$dirName"
        from(sourcePath) {
            into(dirName)
        }
    }

    // Check if copy was successful
    doLast {
        project.checkCopySuccess(directories.toList(), destinationBasePath)
    }
}

tasks.register("cleaniOSTestProjects") {
    group = "ios tests"
    doLast {
        val folderPath = project.file("testdata/tempIOSGradleTests")

        if (folderPath.exists()) {
            delete(folderPath)
        }
    }
}

tasks.register("assembleInSpecificFolder") {
    doLast {
        // Define the directory where you want to run the assemble task
        val buildDir = project.file("testData/tempIOSGradleTests/ios-app")

        // Execute the assemble task in that directory
        exec {
            workingDir(buildDir)
            commandLine("gradle", "assemble")
        }
    }
}

val prepareProjectsiOS = tasks.register("prepareProjectsiOS") {
    group = "ios tests"
    doLast {
        val runWithPluginClasspath: Boolean = true
        val pathToProjects: String = "testdata/tempIOSGradleTests"
        val rootPath = project.rootDir.absolutePath
        val assetsPath = "testData/iOSTestsAssets"

        validateDirectories(rootPath, assetsPath)

        file(pathToProjects).absoluteFile.listFiles()?.forEach { projectDir ->
            if (projectDir.isDirectory) {
                processProjectDirectory(projectDir, runWithPluginClasspath, assetsPath, rootPath)
            }
        } ?: error("No projects found at $pathToProjects")
    }
}

fun validateDirectories(rootPath: String, assetsPath: String) {
    val implementationDir = file("../../sources").absoluteFile
    require(implementationDir.exists()) { "Amper plugin project not found at $implementationDir" }

    val assetsDir = file(assetsPath)
    require(assetsDir.exists() && assetsDir.isDirectory) { "Assets directory not found at $assetsPath" }
}

fun processProjectDirectory(projectDir: File, runWithPluginClasspath: Boolean, assetsPath: String, rootPath: String) {
    prepareProject(projectDir, runWithPluginClasspath, file("../../sources").absoluteFile)
    compileAndExecuteGradleTasks(projectDir, rootPath)
    configureXcodeProject(projectDir)
    copyAssets(projectDir, assetsPath)
}

fun compileAndExecuteGradleTasks(projectDir: File, rootPath: String) {
    listOf("assemble").forEach { task ->
        println("Executing './gradlew $task' in ${projectDir.name}")
        ProcessBuilder("/bin/sh", "-c", "$rootPath/gradlew $task")
            .directory(projectDir)
            .redirectErrorStream(true)
            .start().apply {
                inputStream.bufferedReader().forEachLine { println(it) }
                waitFor()
            }
    }
}

fun configureXcodeProject(projectDir: File) {
    val xcodeprojPath = if (projectDir.name == "ios-app") {
        File(projectDir, "build/apple/ios-app/ios-app.xcodeproj/project.pbxproj")
    } else {
        File(projectDir, "ios-app/build/apple/ios-app/ios-app.xcodeproj/project.pbxproj")
    }
    addDeploymentTarget(xcodeprojPath, "16.0", "iosApp.iosApp")

    val xcodeBuildCommand = "xcrun xcodebuild -project ${xcodeprojPath.parent} -scheme iosApp -configuration Debug OBJROOT=${projectDir.path}/tmp SYMROOT=${projectDir.path}/bin -arch arm64 -derivedDataPath ${projectDir.path}/derivedData -sdk iphonesimulator"
    executeCommandInDirectory(xcodeBuildCommand, projectDir)
}

fun executeCommandInDirectory(command: String, directory: File) {
    ProcessBuilder("/bin/sh", "-c", command)
        .directory(directory)
        .redirectErrorStream(true)
        .start().apply {
            inputStream.bufferedReader().forEachLine { println(it) }
            waitFor()
        }
}

fun copyAssets(projectDir: File, assetsPath: String) {
    val destinationPath = File(projectDir, "build/apple/ios-app")
    project.copy {
        from(file(assetsPath))
        into(destinationPath)
    }
    println("Copied assets to ${destinationPath.path}")
}

fun addDeploymentTarget(xcodeprojPath: File, newDeploymentTarget: String, newBundleIdentifier: String) {
    // Read the content of the file
    val content = xcodeprojPath.readText()

    // Define the regular expression pattern to find INFOPLIST_FILE
    val infoPlistRegex = Regex("(INFOPLIST_FILE\\s*=\\s*\".*?\";)")
    // Define the regular expression pattern to find PRODUCT_BUNDLE_IDENTIFIER
    val bundleIdentifierRegex = Regex("(PRODUCT_BUNDLE_IDENTIFIER\\s*=\\s*\".*?\";)")

    // Find the match for INFOPLIST_FILE
    val infoPlistMatchResult = infoPlistRegex.find(content)

    // Find the match for PRODUCT_BUNDLE_IDENTIFIER
    val bundleIdentifierMatchResult = bundleIdentifierRegex.find(content)

    // Variable to hold the updated content, start with original
    var updatedContent = content

    // Check and update INFOPLIST_FILE
    if (infoPlistMatchResult != null) {
        val infoPlistMatch = infoPlistMatchResult.value
        val infoPlistReplacement = "$infoPlistMatch\n\t\t\tIPHONEOS_DEPLOYMENT_TARGET = $newDeploymentTarget;"
        updatedContent = updatedContent.replace(infoPlistMatch, infoPlistReplacement)
        println("IPHONEOS_DEPLOYMENT_TARGET added")
    } else {
        println("INFOPLIST_FILE not found in the file.")
    }

    // Check and update PRODUCT_BUNDLE_IDENTIFIER
    if (bundleIdentifierMatchResult != null) {
        val bundleIdentifierMatch = bundleIdentifierMatchResult.value
        val bundleIdentifierReplacement = "PRODUCT_BUNDLE_IDENTIFIER = \"$newBundleIdentifier\";"
        updatedContent = updatedContent.replace(bundleIdentifierMatch, bundleIdentifierReplacement)
        println("PRODUCT_BUNDLE_IDENTIFIER updated")
    } else {
        println("PRODUCT_BUNDLE_IDENTIFIER not found in the file.")
    }

    // Write the updated content back to the file
    xcodeprojPath.writeText(updatedContent)
}





fun prepareProject(projectDir: File, runWithPluginClasspath: Boolean, implementationDir: File) {
    val gradleFile = projectDir.resolve("settings.gradle.kts")
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
        if (!gradleFileText.contains("version(")) {
            gradleFile.writeText(updatedText)
        }

        require(gradleFile.readText().contains("version(")) {
            "Gradle file must have 'version(' after replacement: $gradleFile"
        }
    }

    if (gradleFile.readText().contains("includeBuild(\".\"")) {
        throw GradleException("Example project ${projectDir.name} has a relative includeBuild() call, but it's run within Amper tests from a moved directory. Add a comment '<REMOVE_LINE_IF_RUN_WITH_PLUGIN_CLASSPATH>' on the same line if this included build is for Amper itself (will be removed if Amper is on the classpath).")
    }
}

tasks.register("installAndTestiOSApps") {
    group = "ios tests"

    doLast {
        val rootDirectory = project.file("testdata/tempIOSGradleTests")
        val projects = rootDirectory.listFiles()?.filter { it.isDirectory }
            ?: throw GradleException("No projects found in $rootDirectory.")

        // Ensure there are projects to process.
        if (projects.isEmpty()) {
            throw GradleException("No projects found in $rootDirectory.")
        }

        projects.forEach { projectDir ->
            println("Processing project in directory: ${projectDir.name}")
            val appDirectory = File(projectDir, "bin/Debug-iphonesimulator")

            val appFiles = appDirectory.listFiles { _, name -> name.endsWith(".app") } ?: emptyArray()

            if (appFiles.isNotEmpty()) {
                val appFile = appFiles.first()
                println("Installing app bundle: ${projectDir.name}")
                installAndRunAppBundle(appFile)
                //updateClassnameAndRenameFile(projectDir.name)

            } else {
                throw GradleException("No app files found in $projectDir.")
            }
        }
        val outputDir = File(project.projectDir, "androidUITestsAssets/reports")
        //combineJUnitReports(outputDir.absolutePath, "main.xml")
    }
}

fun installAndRunAppBundle(appFile: File) {
    val appBundleId = "iosApp.iosApp"
    val testHostAppBundleId = "iosApp.iosAppUITests.xctrunner"
    val xctestBundleId = "iosApp.iosAppUITests"

    idb(params = arrayOf("install",appFile.absolutePath))
    idb(
        params = arrayOf(
            "--log", "ERROR",
            "xctest",
            "run",
            "ui",
            xctestBundleId,
            appBundleId,
            testHostAppBundleId

        )
    )
    println("Uninstalling $appBundleId")
    idb(params = arrayOf("uninstall",appBundleId))
}



//endregion

