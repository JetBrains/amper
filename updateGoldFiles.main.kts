/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

@file:OptIn(ExperimentalPathApi::class)
@file:Repository("https://repo.gradle.org/gradle/libs-releases")
@file:DependsOn("org.gradle:gradle-tooling-api:8.10")

import org.gradle.tooling.GradleConnectionException
import org.gradle.tooling.GradleConnector
import java.io.File
import java.nio.file.Path
import kotlin.io.path.*

private val amperRootDir: Path = __FILE__.toPath().absolute().parent
private val schemaModuleDir = amperRootDir / "sources/frontend/schema"
private val testResourcesDir = schemaModuleDir / "testResources"
private val testResourcePathRegex = Regex("(${Regex.escape(testResourcesDir.absolutePathString())})[^),\"'\n\r]*")

fun updateGoldFiles() {
    // regenerate .tmp files from broken tests
    runSchemaTestsWithGradle()

    schemaModuleDir.walk()
        .filter { it.name.endsWith(".tmp") }
        .forEach { tmpResultFile ->
            updateGoldFileFor(tmpResultFile)
        }
}

fun runSchemaTestsWithGradle() {
    println("Running schema tests in Gradle to generate .tmp result files...")
    try {
        GradleConnector.newConnector()
            .forProjectDirectory(amperRootDir.toFile())
            .useBuildDistribution()
            .connect().use { connection ->
                connection.newBuild()
                    .forTasks(":sources:frontend:schema:check")
                    .addArguments("--continue")
                    .setStandardOutput(System.out)
                    .run()
            }
        println()
        println("Gradle build succeeded, which means no new .tmp files were generated.")
    } catch (e: GradleConnectionException) {
        println()
        println("Gradle build failed, but it's ok if it's because of the failed schema tests.")
    }
    println()
}

fun updateGoldFileFor(tmpResultFile: Path) {
    val realGoldFile = goldFileFor(tmpResultFile)
    println("Replacing ${realGoldFile.name} with the contents of ${tmpResultFile.name}")
    val newGoldContent = tmpResultFile.contentsWithVariables()
    realGoldFile.writeText(newGoldContent)
    tmpResultFile.deleteExisting()
}

fun goldFileFor(tmpResultFile: Path): Path = tmpResultFile.resolveSibling(tmpResultFile.name.removeSuffix(".tmp"))

/**
 * Gets the contents of this temp file with the paths replaced with variables, as they are usually in gold files to
 * make them machine-/os-independent.
 */
fun Path.contentsWithVariables(): String = readText().replace(testResourcePathRegex) { match ->
    // See variable substitution in schema/helper/util.kt
    match.value
        // {{ testResources }} is used for the "base" path, which is the dir containing the gold file
        .replace(parent.absolutePathString(), "{{ testResources }}")
        // {{ testProcessDir }} is the dir in which tests are run, which is the schema module
        .replace(schemaModuleDir.absolutePathString(), "{{ testProcessDir }}")
        .replace(File.separator, "{{ fileSeparator }}")
}

updateGoldFiles()
