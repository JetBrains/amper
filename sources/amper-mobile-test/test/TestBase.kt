import org.junit.jupiter.api.condition.OS
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.io.OutputStream
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.Path
import kotlin.io.path.absolutePathString
import kotlin.io.path.copyToRecursively
import kotlin.io.path.createDirectories
import kotlin.io.path.createTempDirectory
import kotlin.io.path.deleteRecursively
import kotlin.io.path.exists

/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */
val destinationBasePath = Path("tempProjects")

open class TestBase {

    val destinationBasePath: Path = Paths.get("tempProjects")
    val gitRepoUrl: String = "ssh://git@git.jetbrains.team/amper/amper-external-projects.git"

    // Copies the project either from local path or Git if not found locally
    fun copyProject(projectName: String, sourceDirectory: String) {
        // Construct the local source directory path
        var sourceDir = Path(sourceDirectory).resolve(projectName)

        // Check if the source directory exists locally
        if (!sourceDir.exists()) {
            println("Local source directory not found: $sourceDir. Attempting to clone from Git...")

            // Clone the project from Git repository into a temporary directory
            val tempDir = createTempDirectory()

            try {
                // Run the Git clone command
                val gitCloneCommand = listOf("git", "clone", gitRepoUrl, tempDir.toAbsolutePath().toString())
                val process = ProcessBuilder(gitCloneCommand)
                    .redirectErrorStream(true)
                    .start()

                val exitCode = process.waitFor()
                if (exitCode != 0) {
                    // Log output for debugging
                    val errorOutput = process.inputStream.bufferedReader().use { it.readText() }
                    throw RuntimeException("Git clone failed with exit code $exitCode. Output: $errorOutput")
                }

                println("Git clone successful. Temp directory: $tempDir")

                // Verify if the repo has been cloned by checking the .git folder
                val gitFolder = tempDir.resolve(".git")
                if (!gitFolder.exists()) {
                    throw RuntimeException("Git clone completed but .git folder is missing. Something went wrong.")
                }
                println("Git repository successfully cloned.")

                // Project is expected to be in the root of the cloned repository
                sourceDir = tempDir.resolve(projectName)

                // Log the path where the project is expected
                println("Project path being copied from: $sourceDir")

                if (!sourceDir.exists()) {
                    throw RuntimeException("Project directory does not exist in the cloned repository at $sourceDir")
                }
            } catch (ex: IOException) {
                throw RuntimeException("Failed to clone Git repository", ex)
            }
        }

        // Destination path for the copied project
        val destinationProjectPath = destinationBasePath.resolve(projectName)

        try {
            // Ensure the destination directory exists
            destinationBasePath.createDirectories()

            // Copy the project files from source to destination
            println("Copying project from $sourceDir to $destinationProjectPath")
            sourceDir.copyToRecursively(target = destinationProjectPath, followLinks = false, overwrite = true)
        } catch (ex: IOException) {
            throw RuntimeException("Failed to copy files from $sourceDir to $destinationProjectPath", ex)
        }
    }
}
fun putAmperToGradleFile(projectDir: File, runWithPluginClasspath: Boolean) {
        val gradleFile = File("tempProjects/${projectDir.name}/settings.gradle.kts")
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

    fun assembleTargetApp(projectDir: File) {
        val tasks = listOf("assemble")
        val osName = System.getProperty("os.name").toLowerCase()
        val gradlewFileName = if (osName.contains("win")) "gradlew.bat" else "gradlew"
        val gradlewPath = File(projectDir, "../../../../$gradlewFileName")

        if (!gradlewPath.exists()) {
            println("gradlew file does not exist in ${gradlewPath}")
            throw FileNotFoundException("gradlew file does not exist in ${gradlewPath.absolutePath}")
        }

        tasks.forEach { task ->
            try {
                println("Executing '$task' in ${projectDir.name}")
                val processBuilder = ProcessBuilder(gradlewPath.absolutePath, task)
                    .directory(projectDir)
                    .redirectErrorStream(true)

                val osName = System.getProperty("os.name").toLowerCase()
                if (osName.contains("mac") && isRunningInTeamCity()) {
                    println("Running on macOS and in TeamCity. Setting environment variables.")
                    processBuilder.environment()["ANDROID_HOME"] = System.getenv("ANDROID_HOME") ?: "/Users/admin/android-sdk/"
                    processBuilder.environment()["PATH"] = System.getenv("PATH")
                } else {
                    println("Not on macOS in TeamCity. No additional environment variables set.")
                }

                val process = processBuilder.start()

                println("Started './gradlew $task' with process id: ${process.pid()} in ${projectDir.name}")

                process.inputStream.bufferedReader().use { reader ->
                    reader.forEachLine { println(it) }
                }

                val exitCode = process.waitFor()
                println("Finished './gradlew $task' with exit code: $exitCode in ${projectDir.name}")
                if (exitCode != 0) {
                    println("Error executing './gradlew $task' in ${projectDir.name}")
                    throw RuntimeException("Execution of './gradlew $task' failed in ${projectDir.name}")
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

    fun executeCommand(
        command: List<String>,
        standardOut: OutputStream,
        env: Map<String, String> = emptyMap()
    ) {
        val process = ProcessBuilder()
            .command(command)
            .redirectErrorStream(true)
            .apply {
                environment().putAll(env)
            }
            .start()

        process.inputStream.use { input ->
            input.bufferedReader().forEachLine { line ->
                standardOut.write((line + "\n").toByteArray())
                standardOut.flush()
            }
        }

        process.waitFor()
    }


    fun executeCommand(
        command: List<String>,
        standardOut: OutputStream,
        workingDirectory: File,
        env: Map<String, String> = emptyMap()
    ) {
        val process = ProcessBuilder()
            .command(command)
            .directory(workingDirectory)
            .redirectErrorStream(true)
            .apply {
                environment().putAll(env)
            }
            .start()

        process.inputStream.use { input ->
            input.bufferedReader().forEachLine { line ->
                standardOut.write((line + "\n").toByteArray())
                standardOut.flush()
            }
        }

        process.waitFor()
    }


fun executeCommand(
    command: List<String>,
    standardOut: OutputStream,
    standardErr: OutputStream,
    env: Map<String, String> = emptyMap()
) {
    val process = ProcessBuilder()
        .command(command)
        .redirectErrorStream(false)  // Изменено на false для разделения stdout и stderr
        .apply {
            environment().putAll(env)
        }
        .start()

    process.inputStream.use { input ->
        input.bufferedReader().forEachLine { line ->
            standardOut.write((line + "\n").toByteArray())
            standardOut.flush()
        }
    }

    process.errorStream.use { error ->
        error.bufferedReader().forEachLine { line ->
            standardErr.write((line + "\n").toByteArray())
            standardErr.flush()
        }
    }

    process.waitFor()
}
private fun isRunningInTeamCity(): Boolean {
    return System.getenv("TEAMCITY_VERSION") != null
}