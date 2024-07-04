import org.junit.jupiter.api.condition.OS
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import kotlin.io.path.Path
import kotlin.io.path.absolutePathString
import kotlin.io.path.exists

/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */
val destinationBasePath = Paths.get("tempProjects")

open class TestBase {

    fun copyProject(projectName: String, sourceDirectory: String) {
        val sourceDir = Paths.get(sourceDirectory).resolve(projectName)

        if (!Files.exists(sourceDir)) {
            throw IllegalArgumentException("Source directory does not exist: $sourceDir")
        }

        val destinationProjectPath = destinationBasePath.resolve(projectName)

        try {
            if (!Files.exists(destinationBasePath)) {
                Files.createDirectories(destinationBasePath)
            }

            Files.walk(sourceDir).use { stream ->
                stream.forEach { source ->
                    val destination = destinationProjectPath.resolve(sourceDir.relativize(source))
                    if (Files.isDirectory(source)) {
                        if (!Files.exists(destination)) {
                            Files.createDirectories(destination)
                        }
                    } else {
                        Files.copy(source, destination, StandardCopyOption.REPLACE_EXISTING)
                    }
                }
            }
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
        listOf("assemble").forEach { task ->
            val gradlewPath = if (OS.current() == OS.WINDOWS) Path("../../gradlew.bat") else Path("../../gradlew")
            if (gradlewPath.exists()) {
                println("Executing '$task' in ${projectDir.name}")
                val process = ProcessBuilder(gradlewPath.absolutePathString(), task)
                    .directory(projectDir)
                    .redirectErrorStream(true)
                    .start()
                println("Started './gradlew $task' with process id: ${process.pid()} in ${projectDir.name}")
                process.apply {
                    inputStream.bufferedReader().use { reader ->
                        reader.forEachLine { println(it) }
                    }
                    val exitCode = waitFor()
                    println("Finished './gradlew $task' with exit code: $exitCode in ${projectDir.name}")
                    if (exitCode != 0) {
                        println("Error executing './gradlew $task' in ${projectDir.name}")
                        throw RuntimeException("Execution of './gradlew $task' failed in ${projectDir.name}")
                    }
                }
            } else {
                println("gradlew file does not exist in ${projectDir.name}")
                throw FileNotFoundException("gradlew file does not exist in ${projectDir.name}")
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