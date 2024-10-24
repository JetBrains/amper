import kotlinx.coroutines.runBlocking
import org.jetbrains.amper.processes.runProcess
import org.jetbrains.amper.processes.runProcessAndCaptureOutput
import org.jetbrains.amper.test.SimplePrintOutputListener
import org.jetbrains.amper.test.checkExitCodeIsZero
import java.io.*
import kotlin.io.path.*

open class iOSBaseTest(): TestBase() {

    private fun prepareExecution(
        projectName: String,
        projectPath: String,
        projectAction: suspend (String) -> Unit,
    ) = runBlocking {
        val appFile = File("iOSTestsAssets/app/Debug-iphonesimulator/iosApp.app")

        getOrCreateRemoteSession()
        copyProject(projectName, projectPath)
        installTestBundleForUITests()
        projectAction(projectName)
        installAndTestiOSApp(projectName)

    }

    internal fun testRunnerGradle(projectName: String) = prepareExecution(projectName, "../../examples-gradle/") {
        prepareProjectsiOSforGradle(it)
    }

    internal fun testRunnerPure(projectName: String) = prepareExecution(projectName, "../../examples-standalone/") {
        prepareProjectiOSForStandalone(it)
    }

    @Throws(InterruptedException::class, IOException::class)
    private suspend fun installTestBundleForUITests() {
        val absolutePath = "iOSTestsAssets/iosAppUITests-Runner.app"
        idb("install", absolutePath)
        idb("xctest", "install", "$absolutePath/Plugins/iosAppUITests.xctest")
    }

    private suspend fun prepareProjectsiOSforGradle(projectDir: String) {
        val runWithPluginClasspath = true
        val projectDirectory = File("tempProjects" + File.separator + projectDir)
        val rootPath = File(".").absolutePath
        val assetsPath = "iOSTestsAssets"

        validateDirectories(rootPath, assetsPath)

        if (projectDirectory.exists() && projectDirectory.isDirectory) {
            processProjectDirectory(projectDirectory, runWithPluginClasspath, assetsPath, rootPath)
        } else {
            println("Project directory '${projectDirectory.absolutePath}' does not exist or is not a directory.")
        }
    }

    private fun validateDirectories(rootPath: String, assetsPath: String) {
        val implementationDir = File("../../sources").absoluteFile
        require(implementationDir.exists()) { "Amper plugin project not found at $implementationDir" }

        val assetsDir = File(assetsPath)
        require(assetsDir.exists() && assetsDir.isDirectory) { "Assets directory not found at $assetsPath" }
    }

    private suspend fun idb(vararg params: String): String {
        val idbCompanion = getOrCreateRemoteSession()
        val command = listOf("/Users/admin/Library/Python/3.9/bin/idb", *params) // hardcode to ci. because path var not changing now

        println("Executing IDB: $command")
        val result = runProcessAndCaptureOutput(
            command = command,
            environment = mapOf("IDB_COMPANION" to idbCompanion),
            outputListener = SimplePrintOutputListener(),
        ).checkExitCodeIsZero()
        return result.stdout
    }

    private suspend fun getOrCreateRemoteSession(): String {
        val result = runProcessAndCaptureOutput(
            command = listOf("./scripts/session.sh", "-s", "sessionInfoPath", "-n", "Amper UI Test", "create"),
            outputListener = SimplePrintOutputListener(),
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
            command = listOf("./scripts/session.sh", "-s", "sessionInfoPath", "-n", "Amper UI Test", "delete"),
            outputListener = SimplePrintOutputListener(),
        ).checkExitCodeIsZero()
    }

    private suspend fun processProjectDirectory(
        projectDir: File,
        runWithPluginClasspath: Boolean,
        assetsPath: String,
        rootPath: String
    ) {
        putAmperToGradleFile(projectDir, runWithPluginClasspath)
        assembleTargetApp(projectDir)
        configureXcodeProject(projectDir)
    }

    private suspend fun configureXcodeProject(projectDir: File) {
        val baseProjectPath: String
        val pbxprojPath: File
        val xCodeProjectPath: File

        val iosAppFolder = File("${projectDir.path}/ios-app")

        if (iosAppFolder.exists()) {
            baseProjectPath = "${projectDir.path}/ios-app/"
            pbxprojPath = File("$baseProjectPath/build/apple/ios-app/ios-app.xcodeproj/project.pbxproj")
            xCodeProjectPath = File("$baseProjectPath/build/apple/ios-app/ios-app.xcodeproj")
        } else {
            baseProjectPath = "${projectDir.path}/"
            pbxprojPath =
                File("$baseProjectPath/build/apple/${projectDir.name}/${projectDir.name}.xcodeproj/project.pbxproj")
            xCodeProjectPath = File("$baseProjectPath/build/apple/${projectDir.name}/${projectDir.name}.xcodeproj")
        }

        if (!pbxprojPath.exists()) {
            throw FileNotFoundException("File does not exist: $pbxprojPath")
        }

        updateAppTarget(pbxprojPath, "16.0", "iosApp.iosApp")

        val xcodeBuildCommand =
            "xcrun xcodebuild -project ${xCodeProjectPath.absolutePath} -scheme iosApp -configuration Debug OBJROOT=${projectDir.parent}/tmp SYMROOT=${projectDir.absoluteFile.parentFile.parentFile}/iOSTestsAssets/app -arch arm64 -derivedDataPath ${projectDir.path}/derivedData -sdk iphonesimulator"

        executeCommandInDirectory(xcodeBuildCommand, File(xCodeProjectPath.parent))
    }

    private suspend fun executeCommandInDirectory(command: String, directory: File) {
        runProcess(
            workingDir = directory.toPath(),
            command = listOf("/bin/sh", "-c", command),
            redirectErrorStream = true,
            outputListener = SimplePrintOutputListener(),
        )
    }

    private fun updateAppTarget(xcodeprojPath: File, newDeploymentTarget: String, newBundleIdentifier: String) {
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
                    updatedAppTarget + "\n\t\tPRODUCT_BUNDLE_IDENTIFIER = \"$newBundleIdentifier\";"
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

    private suspend fun installAndTestiOSApp(projectDirPath: String) {
        val projectDir = File("tempProjects/$projectDirPath")
        if (!projectDir.exists() || !projectDir.isDirectory) {
            throw IllegalArgumentException("Invalid project directory: $projectDir")
        }

        println("Processing project in directory: ${projectDir.name}")

        val primaryAppDirectory = File("iOSTestsAssets/app/Debug-iphonesimulator")
        val secondaryAppDirectory = File("tempProjects/$projectDirPath/build/tasks/_$projectDirPath"+"_buildIosAppIosSimulatorArm64/bin/Debug-iphonesimulator")

        val appFiles = primaryAppDirectory.listFiles { _, name -> name.endsWith(".app") }
            ?: secondaryAppDirectory.listFiles { _, name -> name.endsWith(".app") }
            ?: emptyArray()

        if (appFiles.isNotEmpty()) {
            val appFile = appFiles.first()
            println("Installing app bundle: ${projectDir.name}")
            installAndRunAppBundle(appFile)
        } else {
            throw AppNotFoundException("No app files found in $primaryAppDirectory or $secondaryAppDirectory.")
        }
    }

    private suspend fun installAndRunAppBundle(appFile: File) {
        val appBundleId = "iosApp.iosApp"
        val testHostAppBundleId = "iosApp.iosAppUITests.xctrunner"
        val xctestBundleId = "iosApp.iosAppUITests"

        idb("install", appFile.absolutePath)
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

    private suspend fun configureXcodeProjectForStandalone(projectDir: File) {
        val xcodeprojPath = File(
            projectDir,
            "build/tasks/_${projectDir.name}_buildIosAppIosSimulatorArm64/build/${projectDir.name}.xcodeproj/project.pbxproj"
        )
        if (!xcodeprojPath.exists()) {
            throw FileNotFoundException("File does not exist: $xcodeprojPath")
        }

        updateAppTargetPure(xcodeprojPath, "16.0", "iosApp.iosApp")


        println("Rebuild app with updated target:")


        val xcodeBuildCommand =
            "xcrun xcodebuild -project ${xcodeprojPath.parentFile.absolutePath} -scheme iosSimulatorArm64 -configuration Debug OBJROOT=${projectDir.parent}/tmp SYMROOT=${projectDir.absoluteFile.parentFile.parentFile}/iOSTestsAssets/app -arch arm64 -derivedDataPath ${projectDir.path}/derivedData -sdk iphonesimulator"
        executeCommandInDirectory(xcodeBuildCommand, projectDir)
    }

    private fun updateAppTargetPure(xcodeprojPath: File, newDeploymentTarget: String, newBundleIdentifier: String) {
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
                    updatedBuildSettings + "\n\t\tPRODUCT_BUNDLE_IDENTIFIER = \"$newBundleIdentifier\";"
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


    private suspend fun prepareProjectiOSForStandalone(projectDir: String) {
        val projectDir = Path("tempProjects") / projectDir
        if (projectDir.exists() && projectDir.isDirectory()) {
            runAmper(
                workingDir = projectDir,
                args = listOf("task", ":${projectDir.name}:buildIosAppIosSimulatorArm64"),
                assertEmptyStdErr = false, // warn
            )
            configureXcodeProjectForStandalone(projectDir.toFile())
        } else {
            println("The path '$projectDir' does not exist or is not a directory.")
        }
    }
}
