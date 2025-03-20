/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

@file:OptIn(ExperimentalPathApi::class)

import java.io.File
import java.nio.file.Path
import kotlin.io.path.*

private val amperRootDir: Path = __FILE__.toPath().absolute().parent
private val schemaModuleDir = amperRootDir / "sources/frontend/schema"
private val testResourcesDir = schemaModuleDir / "testResources"
private val testResourcePathRegex = Regex("(${Regex.escape(testResourcesDir.absolutePathString())})[^),\"'\n\r]*")

fun updateGoldFiles() {
    // regenerate .tmp files from broken tests
    runSchemaTests()

    schemaModuleDir.walk()
        .filter { it.name.endsWith(".tmp") }
        .forEach { tmpResultFile ->
            updateGoldFileFor(tmpResultFile)
        }
}

@Suppress("PROCESS_BUILDER_START_LEAK")
fun runSchemaTests() {
    println("Running schema tests to generate .tmp result files...")
    val isWindows = System.getProperty("os.name").startsWith("Win", ignoreCase = true)
    val amperScript = if (isWindows) "amper.bat" else "amper"
    val exitCode = ProcessBuilder(amperScript, "test", "-m", "schema")
        .inheritIO()
        .start()
        .waitFor()
    println()
    if (exitCode == 0) {
        println("Tests succeeded, which means no new .tmp files were generated.")
    } else {
        println("Tests failed, but it's ok if it's because of the failed schema tests.")
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
