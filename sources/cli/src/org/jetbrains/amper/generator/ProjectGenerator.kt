/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.generator

import com.github.ajalt.mordant.terminal.Terminal
import io.github.classgraph.ClassGraph
import org.jetbrains.amper.cli.userReadableError
import org.jetbrains.amper.core.AmperBuild
import org.jetbrains.amper.core.system.OsFamily
import org.jetbrains.amper.util.substituteTemplatePlaceholders
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.outputStream
import kotlin.io.path.pathString

internal class ProjectGenerator(private val terminal: Terminal) {

    private val logger: Logger = LoggerFactory.getLogger(javaClass)

    fun initProject(template: String?, directory: Path) {
        val allTemplateFiles = ClassGraph().acceptPaths("templates").scan().use { scanResult ->
            scanResult.allResources.paths.map { pathString ->
                check(pathString.startsWith("templates/")) {
                    "Resource path must start with templates/: $pathString"
                }
                pathString
            }
        }

        val allTemplateNames = allTemplateFiles.map {
            Path(it).getName(1).pathString
        }.distinct().sorted()

        if (template == null) {
            userReadableError(
                "Please specify a template (template name substring is sufficient).\n\n" +
                        "Available templates: ${allTemplateNames.joinToString(" ")}"
            )
        }

        if (directory.exists() && !directory.isDirectory()) {
            userReadableError("Project root is not a directory: $directory")
        }

        val matchedTemplates = allTemplateNames.filter {
            it.contains(template, ignoreCase = true)
        }

        if (matchedTemplates.isEmpty()) {
            userReadableError(
                "No templates were found matching '$template'\n\n" +
                        "Available templates: ${allTemplateNames.joinToString(" ")}"
            )
        }
        if (matchedTemplates.size > 1) {
            userReadableError(
                "Multiple templates (${
                    matchedTemplates.sorted().joinToString(" ")
                }) were found matching '$template'\n\n" +
                        "Available templates: ${allTemplateNames.joinToString(" ")}"
            )
        }

        val matchedTemplate = matchedTemplates.single()
        terminal.println("Extracting template '$matchedTemplate' to $directory")

        val resourcePrefix = "templates/$matchedTemplate/"
        val templateFiles = allTemplateFiles
            .filter { it.startsWith(resourcePrefix) }
            .map { it to it.removePrefix(resourcePrefix) }
        check(templateFiles.isNotEmpty()) {
            "No files was found for template '$matchedTemplate'. All template files:\n" +
                    allTemplateFiles.joinToString("\n")
        }

        checkTemplateFilesConflicts(templateFiles, directory)

        directory.createDirectories()
        for ((resourceName, relativeName) in templateFiles) {
            val path = directory.resolve(relativeName)
            path.parent.createDirectories()
            javaClass.classLoader.getResourceAsStream(resourceName)!!.use { stream ->
                path.outputStream().use { out ->
                    stream.copyTo(out)
                }
            }
        }
        writeWrappers(directory)

        terminal.println("Project template successfully instantiated to $directory")
        terminal.println()
        val exe = if (OsFamily.current.isWindows) "amper.bat build" else "./amper build"
        terminal.println("Now you may build your project with '$exe' or open this folder in IDE with Amper plugin")
    }

    data class AmperWrapper(
        val fileName: String,
        val resourceName: String,
        val executable: Boolean,
        val windowsLineEndings: Boolean,
    )

    val wrappers = listOf(
        AmperWrapper(fileName = "amper", resourceName = "wrappers/amper.template.sh", executable = true, windowsLineEndings = false),
        AmperWrapper(fileName = "amper.bat", resourceName = "wrappers/amper.template.bat", executable = false, windowsLineEndings = true),
    )

    private fun writeWrappers(root: Path) {
        val sha256: String? = System.getProperty("amper.wrapper.dist.sha256")
        if (sha256.isNullOrEmpty()) {
            logger.warn("Amper was not run from amper wrapper, skipping generating wrappers for $root")
            return
        }

        for (w in wrappers) {
            val path = root.resolve(w.fileName)

            substituteTemplatePlaceholders(
                input = javaClass.classLoader.getResourceAsStream(w.resourceName)!!.use { it.readAllBytes() }.decodeToString(),
                outputFile = path,
                placeholder = "@",
                values = listOf(
                    "AMPER_VERSION" to AmperBuild.mavenVersion,
                    "AMPER_DIST_ZIP_SHA256" to sha256,
                ),
                outputWindowsLineEndings = w.windowsLineEndings,
            )

            if (w.executable) {
                val rc = path.toFile().setExecutable(true)
                check(rc) {
                    "Unable to make file executable: $rc"
                }
            }
        }
    }

    private fun checkTemplateFilesConflicts(templateFiles: List<Pair<String, String>>, root: Path) {
        val filesToCheck = templateFiles.map { it.second }
        val alreadyExistingFiles = filesToCheck.filter { root.resolve(it).exists() }
        if (alreadyExistingFiles.isNotEmpty()) {
            userReadableError(
                "Files already exist in the project root:\n" +
                        alreadyExistingFiles.sorted().joinToString("\n").prependIndent("  ")
            )
        }
    }
}
