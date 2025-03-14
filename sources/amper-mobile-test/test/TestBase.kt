import org.jetbrains.amper.processes.runProcess
import org.jetbrains.amper.processes.runProcessAndCaptureOutput
import org.jetbrains.amper.test.AmperCliWithWrapperTestBase
import org.jetbrains.amper.test.LocalAmperPublication
import org.jetbrains.amper.test.SimplePrintOutputListener
import org.jetbrains.amper.test.TestUtil
import java.io.FileNotFoundException
import java.io.IOException
import java.nio.file.Path
import kotlin.io.path.absolutePathString
import kotlin.io.path.copyToRecursively
import kotlin.io.path.createDirectories
import kotlin.io.path.createTempDirectory
import kotlin.io.path.div
import kotlin.io.path.exists
import kotlin.io.path.name
import kotlin.io.path.readLines
import kotlin.io.path.readText
import kotlin.io.path.writeText

/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

open class TestBase : AmperCliWithWrapperTestBase() {

    protected val amperMobileTestsRoot = TestUtil.amperSourcesRoot / "amper-mobile-test"
    protected val tempProjectsDir = amperMobileTestsRoot / "tempProjects"
    protected val scriptsDir = amperMobileTestsRoot / "scripts"

    private val gitRepoUrl: String = "ssh://git.jetbrains.team/amper/amper-external-projects.git"

    private val currentOsName = System.getProperty("os.name")
    val isWindows = currentOsName.lowercase().contains("win")
    val isMacOS = currentOsName.lowercase().contains("mac")

    // Copies the project either from local path or Git if not found locally
    suspend fun copyProject(projectName: String, sourceDirectory: Path) {
        // Construct the local source directory path
        var sourceDir = sourceDirectory / projectName

        // Check if the source directory exists locally
        if (!sourceDir.exists()) {
            println("Local source directory not found: $sourceDir. Attempting to clone from Git...")


            // Clone the project from Git repository into a temporary directory
            val tempDir = createTempDirectory()

            try {
                gitClone(gitRepoUrl, tempDir)

                println("Git clone successful. Temp directory: $tempDir")

                // Verify if the repo has been cloned by checking the .git folder
                val gitFolder = tempDir.resolve(".git")
                if (!gitFolder.exists()) {
                    error("Git clone completed but .git folder is missing. Something went wrong.")
                }
                println("Git repository successfully cloned.")

                // Project is expected to be in the root of the cloned repository
                sourceDir = tempDir.resolve(projectName)

                // Log the path where the project is expected
                println("Project path being copied from: $sourceDir")

                if (!sourceDir.exists()) {
                    error("Project directory does not exist in the cloned repository at $sourceDir")
                }
            } catch (ex: IOException) {
                throw RuntimeException("Failed to clone Git repository", ex)
            }
        }

        // Destination path for the copied project
        val destinationProjectPath = tempProjectsDir.resolve(projectName)

        try {
            // Ensure the destination directory exists
            tempProjectsDir.createDirectories()

            // Copy the project files from source to destination
            println("Copying project from $sourceDir to $destinationProjectPath")
            sourceDir.copyToRecursively(target = destinationProjectPath, followLinks = false, overwrite = true)
            LocalAmperPublication.setupWrappersIn(destinationProjectPath)
        } catch (ex: IOException) {
            throw RuntimeException("Failed to copy files from $sourceDir to $destinationProjectPath", ex)
        }
    }

    private suspend fun gitClone(repoUrl: String, tempDir: Path) {
        val result = runProcessAndCaptureOutput(
            command = listOf("git", "clone", repoUrl, tempDir.toAbsolutePath().toString()),
            environment = buildMap {
                if (isRunningInTeamCity()) {
                    this["GIT_SSH_COMMAND"] =
                        "ssh -i /mnt/agent/temp/buildTmp/.ssh/id_rsa -o UserKnownHostsFile=/dev/null -F none -o IdentitiesOnly=yes -o StrictHostKeyChecking=no"
                }
            },
            redirectErrorStream = true,
        )
        if (result.exitCode != 0) {
            error("Git clone failed with exit code ${result.exitCode}. Output: ${result.stdout}")
        }
    }

    fun putAmperToGradleFile(projectDir: Path, runWithPluginClasspath: Boolean) {
        val gradleFile = projectDir / "settings.gradle.kts"
        require(gradleFile.exists()) { "file not found: $gradleFile" }

        if (runWithPluginClasspath) {
            val lines = gradleFile.readLines().filterNot { "<REMOVE_LINE_IF_RUN_WITH_PLUGIN_CLASSPATH>" in it }
            gradleFile.writeText(lines.joinToString("\n"))

            val gradleFileText = gradleFile.readText()
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

            val updatedText = gradleFile.readText().replace(
                "id(\"org.jetbrains.amper.settings.plugin\")",
                "id(\"org.jetbrains.amper.settings.plugin\") version(\"+\")"
            )
            if (!gradleFileText.contains("version(")) {
                gradleFile.writeText(updatedText)
            }

            require(gradleFile.readText().contains("version(")) {
                "Gradle file must have 'version(' after replacement: $gradleFile"
            }
        }

        if (gradleFile.readText().contains("includeBuild(\".\"")) {
            throw IllegalArgumentException("Example project ${projectDir.name} has a relative includeBuild() call, but it's run within Amper tests from a moved directory. Add a comment '<REMOVE_LINE_IF_RUN_WITH_PLUGIN_CLASSPATH>' on the same line if this included build is for Amper itself (will be removed if Amper is on the classpath).")
        }
    }

    suspend fun assembleTargetApp(projectDir: Path) {
        val tasks = listOf("assemble")
        val gradlewFileName = if (isWindows) "gradlew.bat" else "gradlew"
        // TODO we shouldn't rely on Amper's own wrapper, as we will stop using Gradle completely
        val gradlewPath = TestUtil.amperCheckoutRoot.resolve(gradlewFileName)

        if (!gradlewPath.exists()) {
            println("gradlew file does not exist in $gradlewPath")
            throw FileNotFoundException("gradlew file does not exist in ${gradlewPath.absolutePathString()}")
        }

        tasks.forEach { task ->
            try {
                println("Executing '$task' in ${projectDir.name}")
                val exitCode = runProcess(
                    workingDir = projectDir,
                    command = listOf(gradlewPath.absolutePathString(), task),
                    environment = buildMap {
                        if (isMacOS && isRunningInTeamCity()) {
                            println("Running on macOS and in TeamCity. Setting environment variables.")
                            this["ANDROID_HOME"] = System.getenv("ANDROID_HOME") ?: "/Users/admin/android-sdk/"
                            this["PATH"] = System.getenv("PATH")
                        } else {
                            println("Not on macOS in TeamCity. No additional environment variables set.")
                        }
                    },
                    redirectErrorStream = true,
                    outputListener = SimplePrintOutputListener,
                    onStart = { pid ->
                        println("Started './gradlew $task' with process id: $pid in ${projectDir.name}")
                    },
                )
                println("Finished './gradlew $task' with exit code: $exitCode in ${projectDir.name}")
                if (exitCode != 0) {
                    println("Error executing './gradlew $task' in ${projectDir.name}")
                    error("Execution of './gradlew $task' failed in ${projectDir.name}")
                }
            } catch (e: IOException) {
                println("IOException occurred while executing './gradlew $task' in ${projectDir.name}: ${e.message}")
                throw RuntimeException("Execution of './gradlew $task' failed in ${projectDir.name}", e)
            } catch (e: InterruptedException) {
                println("InterruptedException occurred while executing './gradlew $task' in ${projectDir.name}: ${e.message}")
                throw RuntimeException("Execution of './gradlew $task' was interrupted in ${projectDir.name}", e)
            }
        }
    }
}

private fun isRunningInTeamCity(): Boolean {
    return System.getenv("TEAMCITY_VERSION") != null
}