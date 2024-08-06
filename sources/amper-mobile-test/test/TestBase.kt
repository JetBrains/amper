import org.junit.jupiter.api.condition.OS
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.io.OutputStream
import kotlin.io.path.Path
import kotlin.io.path.absolutePathString
import kotlin.io.path.copyToRecursively
import kotlin.io.path.createDirectories
import kotlin.io.path.exists

/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */
val destinationBasePath = Path("tempProjects")

open class TestBase {

    fun copyProject(projectName: String, sourceDirectory: String) {
        val sourceDir = Path(sourceDirectory).resolve(projectName)

        if (!sourceDir.exists()) {
            throw IllegalArgumentException("Source directory does not exist: $sourceDir")
        }

        val destinationProjectPath = destinationBasePath.resolve(projectName)

        try {
            destinationBasePath.createDirectories()
            sourceDir.copyToRecursively(target = destinationProjectPath, followLinks = false, overwrite = true)
        } catch (ex: IOException) {
            throw RuntimeException("Failed to copy files from $sourceDir to $destinationProjectPath", ex)
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
        val gradlewPath = File(projectDir, gradlewFileName)

        if (!gradlewPath.exists()) {
            println("gradlew file does not exist in ${projectDir.absolutePath}")
            throw FileNotFoundException("gradlew file does not exist in ${projectDir.absolutePath}")
        }

        tasks.forEach { task ->
            try {
                println("Executing '$task' in ${projectDir.name}")
                val process = ProcessBuilder(gradlewPath.absolutePath, task)
                    .directory(projectDir)
                    .redirectErrorStream(true)
                    .start()

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