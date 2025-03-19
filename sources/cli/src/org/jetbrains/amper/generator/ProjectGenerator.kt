/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.generator

import com.github.ajalt.mordant.terminal.Terminal
import org.jetbrains.amper.cli.userReadableError
import org.jetbrains.amper.core.AmperBuild
import org.jetbrains.amper.core.system.OsFamily
import org.jetbrains.amper.util.substituteTemplatePlaceholders
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.isDirectory

internal class ProjectGenerator(private val terminal: Terminal) {

    private val logger: Logger = LoggerFactory.getLogger(javaClass)

    fun initProject(template: AmperProjectTemplate, directory: Path) {
        if (directory.exists() && !directory.isDirectory()) {
            userReadableError("Project root is not a directory: $directory")
        }

        terminal.println("Extracting template '${template.name}' to $directory")
        template.extractTo(outputDir = directory)
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
