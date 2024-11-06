/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

import org.jetbrains.amper.processes.runProcess
import org.jetbrains.amper.processes.runProcessAndCaptureOutput
import org.jetbrains.amper.test.SimplePrintOutputListener
import org.jetbrains.amper.test.TestUtil
import org.jetbrains.amper.test.TestUtil.runTestInfinitely
import org.jetbrains.amper.test.checkExitCodeIsZero
import org.junit.jupiter.api.AfterEach
import java.io.FileNotFoundException
import java.io.IOException
import java.nio.file.Path
import kotlin.io.path.absolutePathString
import kotlin.io.path.deleteRecursively
import kotlin.io.path.div
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import kotlin.io.path.pathString
import kotlin.io.path.readText
import kotlin.io.path.writeText

open class iOSBaseTest(): TestBase() {

    private val iosTestAssetsDir = amperMobileTestsRoot / "iOSTestsAssets"
    private val iosTestAssetsAppDir = iosTestAssetsDir / "app"

    private val sessionScriptPath = scriptsDir / "session.sh"

    @AfterEach
    fun cleanupTestDirs() {
        tempProjectsDir.deleteRecursively()
        iosTestAssetsAppDir.deleteRecursively()
    }

    private fun prepareExecution(
        projectName: String,
        projectPath: Path,
        projectAction: suspend (String) -> Unit,
    ) = runTestInfinitely {
        getOrCreateRemoteSession()
        copyProject(projectName, projectPath)
        installTestBundleForUITests()
        projectAction(projectName)
        installAndTestiOSApp(projectName)
    }

    internal fun testRunnerGradle(projectName: String) {
        val examplesGradleProjectsDir = TestUtil.amperCheckoutRoot.resolve("examples-gradle")
        prepareExecution(projectName, examplesGradleProjectsDir) {
            prepareProjectsiOSforGradle(it)
        }
    }

    internal fun testRunnerPure(projectName: String) {
        val examplesStandaloneProjectsDir = TestUtil.amperCheckoutRoot.resolve("examples-standalone")
        prepareExecution(projectName, examplesStandaloneProjectsDir) {
            prepareProjectiOSForStandalone(it)
        }
    }

    @Throws(InterruptedException::class, IOException::class)
    private suspend fun installTestBundleForUITests() {
        val uiTestsAppPath = iosTestAssetsDir / "iosAppUITests-Runner.app"
        idb("install", uiTestsAppPath.pathString)
        idb("xctest", "install", "$uiTestsAppPath/Plugins/iosAppUITests.xctest")
    }

    private suspend fun prepareProjectsiOSforGradle(projectDir: String) {
        val runWithPluginClasspath = true
        val projectDirectory = tempProjectsDir / projectDir

        validateDirectories()

        if (projectDirectory.exists() && projectDirectory.isDirectory()) {
            processProjectDirectory(projectDirectory, runWithPluginClasspath)
        } else {
            println("Project directory '${projectDirectory.absolutePathString()}' does not exist or is not a directory.")
        }
    }

    private fun validateDirectories() {
        val implementationDir = TestUtil.amperSourcesRoot.toAbsolutePath()
        require(implementationDir.exists()) { "Amper sources not found at $implementationDir" }

        require(iosTestAssetsDir.exists() && iosTestAssetsDir.isDirectory()) {
            "Assets directory not found at $iosTestAssetsDir"
        }
    }

    private suspend fun idb(vararg params: String): String {
        val idbCompanion = getOrCreateRemoteSession()
        val command = listOf("/Users/admin/Library/Python/3.9/bin/idb", *params) // hardcode to ci. because path var not changing now

        println("Executing IDB: $command")
        val result = runProcessAndCaptureOutput(
            command = command,
            environment = mapOf("IDB_COMPANION" to idbCompanion),
            outputListener = SimplePrintOutputListener,
        ).checkExitCodeIsZero()
        return result.stdout
    }

    private suspend fun getOrCreateRemoteSession(): String {
        val result = runProcessAndCaptureOutput(
            command = listOf(sessionScriptPath.pathString, "-s", "sessionInfoPath", "-n", "Amper UI Test", "create"),
            outputListener = SimplePrintOutputListener,
        ).checkExitCodeIsZero()

        result.stdout.lines().forEach {
            if (it.startsWith("IDB_COMPANION")) {
                return it.split('=')[1]
            }
        }
        return ""
    }

    suspend fun deleteRemoteSession() {
        runProcessAndCaptureOutput(
            command = listOf(sessionScriptPath.pathString, "-s", "sessionInfoPath", "-n", "Amper UI Test", "delete"),
            outputListener = SimplePrintOutputListener,
        ).checkExitCodeIsZero()
    }

    private suspend fun processProjectDirectory(
        projectDir: Path,
        runWithPluginClasspath: Boolean
    ) {
        putAmperToGradleFile(projectDir, runWithPluginClasspath)
        assembleTargetApp(projectDir)
        configureXcodeProject(projectDir)
    }

    private suspend fun configureXcodeProject(projectDir: Path) {
        val iosAppFolder = projectDir / "ios-app"
        val moduleDir = if (iosAppFolder.exists()) iosAppFolder else projectDir

        val pbxprojPath = moduleDir / "build/apple/${moduleDir.name}/${moduleDir.name}.xcodeproj/project.pbxproj"
        val xCodeProjectPath = moduleDir / "build/apple/${moduleDir.name}/${moduleDir.name}.xcodeproj"

        if (!pbxprojPath.exists()) {
            throw FileNotFoundException("File does not exist: $pbxprojPath")
        }

        updateAppTarget(pbxprojPath, "16.0", "iosApp.iosApp")

        val objRoot = "${projectDir.parent}/tmp"
        val symRoot = iosTestAssetsAppDir.pathString
        val derivedDataPath = "${projectDir.pathString}/derivedData"
        val xcodeBuildCommand =
            "xcrun xcodebuild -project ${xCodeProjectPath.absolutePathString()} -scheme iosApp -configuration Debug OBJROOT=$objRoot SYMROOT=$symRoot -arch arm64 -derivedDataPath $derivedDataPath -sdk iphonesimulator"

        executeCommandInDirectory(xcodeBuildCommand, xCodeProjectPath.parent)
    }

    private suspend fun executeCommandInDirectory(command: String, directory: Path) {
        runProcess(
            workingDir = directory,
            command = listOf("/bin/sh", "-c", command),
            redirectErrorStream = true,
            outputListener = SimplePrintOutputListener,
        )
    }

    private fun updateAppTarget(xcodeprojPath: Path, newDeploymentTarget: String, newBundleIdentifier: String) {
        val content = xcodeprojPath.readText()
        println("Processing file: $xcodeprojPath")

        val appTargetRegex = Regex("""INFOPLIST_FILE = "Info-iosApp\.plist";[\s\S]*?SDKROOT = iphoneos;""")

        val matches = appTargetRegex.findAll(content).toList()

        if (matches.isNotEmpty()) {
            var updatedContent = content

            for (matchResult in matches) {
                val appTargetSection = matchResult.value

                val deploymentTargetRegex = Regex("IPHONEOS_DEPLOYMENT_TARGET = \\d+\\.\\d+;")
                val bundleIdentifierRegex = Regex("PRODUCT_BUNDLE_IDENTIFIER = \".*?\";")

                var updatedAppTarget = if (deploymentTargetRegex.containsMatchIn(appTargetSection)) {
                    appTargetSection.replace(
                        deploymentTargetRegex,
                        "IPHONEOS_DEPLOYMENT_TARGET = $newDeploymentTarget;"
                    )
                } else {
                    appTargetSection.replace(
                        "SDKROOT = iphoneos;",
                        "SDKROOT = iphoneos;\n\t\t\tIPHONEOS_DEPLOYMENT_TARGET = $newDeploymentTarget;"
                    )
                }

                updatedAppTarget = if (bundleIdentifierRegex.containsMatchIn(appTargetSection)) {
                    updatedAppTarget.replace(
                        bundleIdentifierRegex,
                        "PRODUCT_BUNDLE_IDENTIFIER = \"$newBundleIdentifier\";"
                    )
                } else {
                    "$updatedAppTarget\n\t\tPRODUCT_BUNDLE_IDENTIFIER = \"$newBundleIdentifier\";"
                }

                updatedContent = updatedContent.replace(appTargetSection, updatedAppTarget)
            }

            xcodeprojPath.writeText(updatedContent)
            println("Updated app target with new deployment target and bundle identifier for all configurations.")
        } else {
            println("App target sections not found.")
        }
    }

    class AppNotFoundException(message: String) : Exception(message)

    private suspend fun installAndTestiOSApp(projectName: String) {
        val projectDir = tempProjectsDir / projectName
        if (!projectDir.exists() || !projectDir.isDirectory()) {
            throw IllegalArgumentException("Invalid project directory: $projectDir")
        }

        println("Processing project in directory: ${projectDir.name}")

        val primaryAppDirectory = iosTestAssetsAppDir / "Debug-iphonesimulator"
        val secondaryAppDirectory = tempProjectsDir / "$projectName/build/tasks/_${projectName}_buildIosAppIosSimulatorArm64/bin/Debug-iphonesimulator"

        val appFiles = when {
            primaryAppDirectory.isDirectory() -> primaryAppDirectory.listDirectoryEntries("*.app")
            secondaryAppDirectory.isDirectory() -> secondaryAppDirectory.listDirectoryEntries("*.app")
            else -> emptyList()
        }

        if (appFiles.isNotEmpty()) {
            val appFile = appFiles.first()
            println("Installing app bundle: ${projectDir.name}")
            installAndRunAppBundle(appFile)
        } else {
            throw AppNotFoundException("No app files found in $primaryAppDirectory or $secondaryAppDirectory.")
        }
    }

    private suspend fun installAndRunAppBundle(appFile: Path) {
        val appBundleId = "iosApp.iosApp"
        val testHostAppBundleId = "iosApp.iosAppUITests.xctrunner"
        val xctestBundleId = "iosApp.iosAppUITests"

        idb("install", appFile.absolutePathString())
        val output = idb(
            "--log", "ERROR",
            "xctest",
            "run",
            "ui",
            xctestBundleId,
            appBundleId,
            testHostAppBundleId
        )

        if (!output.contains("iosAppUITests.iosAppUITests/testExample | Status: passed") || output.contains("Error")) {
            error("Test failed with output:\n$output")
        }

        println("Uninstalling $appBundleId")
        idb("uninstall", appBundleId)
    }

    private suspend fun configureXcodeProjectForStandalone(projectDir: Path) {
        val xcodeprojPath = projectDir / "build/tasks/_${projectDir.name}_buildIosAppIosSimulatorArm64/build/${projectDir.name}.xcodeproj/project.pbxproj"
        if (!xcodeprojPath.exists()) {
            throw FileNotFoundException("File does not exist: $xcodeprojPath")
        }

        updateAppTargetPure(xcodeprojPath, "16.0", "iosApp.iosApp")

        println("Rebuild app with updated target:")

        val xcodeBuildCommand =
            "xcrun xcodebuild -project ${xcodeprojPath.parent.absolutePathString()} -scheme iosSimulatorArm64 -configuration Debug OBJROOT=${projectDir.parent}/tmp SYMROOT=${iosTestAssetsAppDir.absolutePathString()} -arch arm64 -derivedDataPath ${projectDir.pathString}/derivedData -sdk iphonesimulator"
        executeCommandInDirectory(xcodeBuildCommand, projectDir)
    }

    private fun updateAppTargetPure(xcodeprojPath: Path, newDeploymentTarget: String, newBundleIdentifier: String) {
        // Read the content of the file
        val content = xcodeprojPath.readText()
        println("Processing file: ${xcodeprojPath.name}")

        // Regular expression to find the build settings blocks
        val buildSettingsRegex = Regex("buildSettings = \\{[\\s\\S]*?\\};")

        // Find all matches instead of just the first
        val matches = buildSettingsRegex.findAll(content).toList()

        // Logging the number of matches found
        println("Found ${matches.size} build settings block(s).")

        if (matches.isNotEmpty()) {
            var updatedContent = content

            // Process each match
            for ((index, matchResult) in matches.withIndex()) {
                val buildSettingsSection = matchResult.value
                println("Processing build settings block $index:\n$buildSettingsSection")

                // Define the regular expressions to check for existing properties
                val deploymentTargetRegex = Regex("IPHONEOS_DEPLOYMENT_TARGET = \\d+\\.\\d+;")
                val bundleIdentifierRegex = Regex("PRODUCT_BUNDLE_IDENTIFIER = \".*?\";")

                // Modify or add the deployment target
                var updatedBuildSettings = if (deploymentTargetRegex.containsMatchIn(buildSettingsSection)) {
                    println("IPHONEOS_DEPLOYMENT_TARGET found, updating value.")
                    buildSettingsSection.replace(deploymentTargetRegex, "IPHONEOS_DEPLOYMENT_TARGET = $newDeploymentTarget;")
                } else {
                    println("IPHONEOS_DEPLOYMENT_TARGET not found, adding it.")
                    buildSettingsSection.replaceFirst("SDKROOT = iphonesimulator;", "SDKROOT = iphonesimulator;\n\t\t\tIPHONEOS_DEPLOYMENT_TARGET = $newDeploymentTarget;")
                }

                // Modify or add the bundle identifier
                updatedBuildSettings = if (bundleIdentifierRegex.containsMatchIn(buildSettingsSection)) {
                    println("PRODUCT_BUNDLE_IDENTIFIER found, updating value.")
                    updatedBuildSettings.replace(bundleIdentifierRegex, "PRODUCT_BUNDLE_IDENTIFIER = \"$newBundleIdentifier\";")
                } else {
                    println("PRODUCT_BUNDLE_IDENTIFIER not found, adding it.")
                    "$updatedBuildSettings\n\t\tPRODUCT_BUNDLE_IDENTIFIER = \"$newBundleIdentifier\";"
                }

                // Replace the old build settings section with the updated one in the file's content
                updatedContent = updatedContent.replace(buildSettingsSection, updatedBuildSettings)
            }

            // Write the updated content back to the file
            xcodeprojPath.writeText(updatedContent)
            println("Updated build settings with new deployment target and bundle identifier for all configurations.")
        } else {
            println("No build settings sections found.")
        }
    }

    private suspend fun prepareProjectiOSForStandalone(projectName: String) {
        val projectDir = tempProjectsDir / projectName
        if (projectDir.exists() && projectDir.isDirectory()) {
            runAmper(
                workingDir = projectDir,
                args = listOf("task", ":$projectName:buildIosAppIosSimulatorArm64"),
                assertEmptyStdErr = false, // warn
            )
            configureXcodeProjectForStandalone(projectDir)
        } else {
            println("The path '$projectDir' does not exist or is not a directory.")
        }
    }
}
