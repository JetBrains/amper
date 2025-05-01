/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.generator

import org.jetbrains.amper.cli.userReadableError
import org.jetbrains.amper.core.AmperBuild
import org.jetbrains.amper.templates.AmperProjectTemplate
import org.jetbrains.amper.templates.TemplateFile
import org.jetbrains.amper.util.substituteTemplatePlaceholders
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.isDirectory

internal object ProjectGenerator {

    private val logger: Logger = LoggerFactory.getLogger(javaClass)

    fun initProject(template: AmperProjectTemplate, targetRootDir: Path) {
        if (targetRootDir.exists() && !targetRootDir.isDirectory()) {
            error("Project root is not a directory: $targetRootDir") // already checked by CLI
        }

        template.extractTo(outputDir = targetRootDir)
        writeWrappers(targetRootDir)
    }

    private fun AmperProjectTemplate.extractTo(outputDir: Path) {
        val files = listFiles()
        checkTemplateFilesConflicts(files, outputDir)
        outputDir.createDirectories()
        files.forEach {
            it.extractTo(outputDir)
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

    private data class AmperWrapper(
        val fileName: String,
        val resourceName: String,
        val executable: Boolean,
        val windowsLineEndings: Boolean,
    )

    private val wrappers = listOf(
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
                    "AMPER_DIST_TGZ_SHA256" to sha256,
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
}
