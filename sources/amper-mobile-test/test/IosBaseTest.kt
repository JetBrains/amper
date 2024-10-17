import java.io.*

open class iOSBaseTest(): TestBase() {

    private fun prepareExecution(projectName: String, projectPath: String, projectAction: (String) -> Unit) {
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
    private fun installTestBundleForUITests() {
        val absolutePath = "iOSTestsAssets/iosAppUITests-Runner.app"
        idb("install", absolutePath)
        idb("xctest", "install", "$absolutePath/Plugins/iosAppUITests.xctest")
    }

    private fun prepareProjectsiOSforGradle(projectDir: String) {
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

    private fun idb(vararg params: String): String {
        val standardOut = ByteArrayOutputStream()
        val standardErr = ByteArrayOutputStream()
        val idbCompanion = getOrCreateRemoteSession()
        val command = listOf("/Users/admin/Library/Python/3.9/bin/idb", *params) // hardcode to ci. because path var not changing now

        println("Executing IDB: $command")
        executeCommand(command, standardOut, standardErr, env = mapOf("IDB_COMPANION" to idbCompanion))

        val cmdOutput = standardOut.toString()
        val cmdError = standardErr.toString()
        println(cmdOutput)
        println(cmdError)

        return cmdOutput
    }

    private fun getOrCreateRemoteSession(): String {
        var idbCompanion = ""

        if (idbCompanion.isEmpty()) {
            val output = executeCommand(
                listOf("./scripts/session.sh", "-s", "sessionInfoPath", "-n", "Amper UI Test", "create"),
            )

            output.lines().forEach {
                println(it)
                if (it.startsWith("IDB_COMPANION")) {
                    idbCompanion = it.split('=')[1]
                }
            }
        }

        return idbCompanion
    }

    fun deleteRemoteSession(): String {
        var idbCompanion = ""

        if (idbCompanion.isEmpty()) {
            val output = executeCommand(
                listOf("./scripts/session.sh", "-s", "sessionInfoPath", "-n", "Amper UI Test", "delete"),
            )

            output.lines().forEach {
                println(it)
                if (it.startsWith("IDB_COMPANION")) {
                    idbCompanion = it.split('=')[1]
                }
            }
        }

        return idbCompanion
    }

    private fun processProjectDirectory(
        projectDir: File,
        runWithPluginClasspath: Boolean,
        assetsPath: String,
        rootPath: String
    ) {
        putAmperToGradleFile(projectDir, runWithPluginClasspath)
        assembleTargetApp(projectDir)
        configureXcodeProject(projectDir)
    }



    private fun configureXcodeProject(projectDir: File) {
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

    private fun executeCommandInDirectory(command: String, directory: File) {
        ProcessBuilder("/bin/sh", "-c", command)
            .directory(directory)
            .redirectErrorStream(true)
            .start().apply {
                inputStream.bufferedReader().use { reader ->
                    reader.forEachLine { println(it) }
                }
                waitFor()
            }
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

    private fun installAndTestiOSApp(projectDirPath: String) {
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

    private fun installAndRunAppBundle(appFile: File) {
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

    private fun configureXcodeProjectForStandalone(projectDir: File) {
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
        println("Processing file: " + xcodeprojPath)

        // Regular expression to find the application target blocks
        val appTargetRegex = Regex("(INFOPLIST_FILE = \"Info-iosSimulatorArm64\\.plist\";[\\s\\S]*?SDKROOT = iphoneos;)")

        // Find all matches instead of just the first
        val matches = appTargetRegex.findAll(content).toList()

        if (matches.isNotEmpty()) {
            var updatedContent = content

            // Process each match
            for (matchResult in matches) {
                val appTargetSection = matchResult.value

                // Define the regular expressions to check for existing properties
                val deploymentTargetRegex = Regex("IPHONEOS_DEPLOYMENT_TARGET = \\d+\\.\\d+;")
                val bundleIdentifierRegex = Regex("PRODUCT_BUNDLE_IDENTIFIER = \".*?\";")

                // Modify or add the deployment target
                var updatedAppTarget = if (deploymentTargetRegex.containsMatchIn(appTargetSection)) {
                    appTargetSection.replace(deploymentTargetRegex, "IPHONEOS_DEPLOYMENT_TARGET = $newDeploymentTarget;")
                } else {
                    appTargetSection.replaceFirst("SDKROOT = iphoneos;", "SDKROOT = iphoneos;\n\t\t\tIPHONEOS_DEPLOYMENT_TARGET = $newDeploymentTarget;")
                }

                // Modify or add the bundle identifier
                updatedAppTarget = if (bundleIdentifierRegex.containsMatchIn(appTargetSection)) {
                    updatedAppTarget.replace(bundleIdentifierRegex, "PRODUCT_BUNDLE_IDENTIFIER = \"$newBundleIdentifier\";")
                } else {
                    updatedAppTarget + "\n\t\tPRODUCT_BUNDLE_IDENTIFIER = \"$newBundleIdentifier\";"
                }

                // Replace the old app target section with the updated one in the file's content
                updatedContent = updatedContent.replace(appTargetSection, updatedAppTarget)
            }

            // Write the updated content back to the file
            xcodeprojPath.writeText(updatedContent)
            println("Updated app target with new deployment target and bundle identifier for all configurations.")
        } else {
            println("App target sections not found.")
        }
    }


    private fun prepareProjectiOSForStandalone(projectDir: String) {
        val projectDir = File("tempProjects" + File.separator + projectDir)
        if (projectDir.exists() && projectDir.isDirectory) {
            val command = " ./amper task :${projectDir.name}:buildIosAppIosSimulatorArm64"

            executeCommandInDirectory(command, projectDir)
            configureXcodeProjectForStandalone(projectDir)
        } else {
            println("The path '$projectDir' does not exist or is not a directory.")
        }
    }
}
