/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.generator

import io.github.classgraph.ClassGraph
import org.jetbrains.amper.cli.userReadableError
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.outputStream

internal object ProjectTemplates {

    val availableTemplates by lazy {
        javaClass.getResource("/templates/list.txt")
            ?.readText()
            ?.trim()
            ?.lines()
            ?.map { line ->
                val (name, description) = line.split(":").map { it.trim() }
                AmperProjectTemplate(name, description)
            }
            ?: error("Template list not found")
    }
}

internal data class AmperProjectTemplate(val name: String, val description: String) {

    fun extractTo(outputDir: Path) {
        val files = getFiles()
        checkTemplateFilesConflicts(files, outputDir)
        outputDir.createDirectories()
        files.forEach {
            it.extractTo(outputDir)
        }
    }

    private fun getFiles(): List<TemplateFile> = ClassGraph()
        .acceptPaths("templates/$name")
        .scan()
        .use { it.allResources.paths }
        .map { TemplateFile(it, it.removePrefix("templates/$name/")) }
        .also {
            // something is very wrong (and out of the user's control) if there are no files
            check(it.isNotEmpty()) { "No files were found for template '$name'" }
        }
}

private data class TemplateFile(val resourcePath: String, val relativePath: String)

private fun TemplateFile.extractTo(directory: Path) {
    val path = directory.resolve(relativePath)
    path.parent.createDirectories()
    javaClass.classLoader.getResourceAsStream(resourcePath)!!.use { stream ->
        path.outputStream().use { out ->
            stream.copyTo(out)
        }
    }
}

private fun checkTemplateFilesConflicts(templateFiles: List<TemplateFile>, outputDir: Path) {
    val filesToCheck = templateFiles.map { it.relativePath }
    val alreadyExistingFiles = filesToCheck.filter { outputDir.resolve(it).exists() }
    if (alreadyExistingFiles.isNotEmpty()) {
        userReadableError(
            "The following files already exist in the output directory and would be overwritten by the generation:\n" +
                    alreadyExistingFiles.sorted().joinToString("\n").prependIndent("  ") + "\n\n" +
                    "Please move, rename, or delete them before running the command again."
        )
    }
}
