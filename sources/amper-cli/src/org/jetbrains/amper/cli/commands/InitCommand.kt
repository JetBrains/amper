/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.cli.commands

import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.PrintMessage
import com.github.ajalt.clikt.core.terminal
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.optional
import com.github.ajalt.clikt.parameters.types.choice
import org.jetbrains.amper.buildinfo.AmperBuild
import org.jetbrains.amper.cli.interactiveSelectList
import org.jetbrains.amper.cli.userReadableError
import org.jetbrains.amper.core.system.OsFamily
import org.jetbrains.amper.templates.AmperProjectTemplate
import org.jetbrains.amper.templates.AmperProjectTemplates
import org.jetbrains.amper.templates.TemplateFile
import org.jetbrains.amper.wrapper.AmperWrappers
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.exists

internal class InitCommand : AmperSubcommand(name = "init") {

    private val template by argument(help = "The name of a project template (leave blank to select interactively from a list)")
        .choice(AmperProjectTemplates.availableTemplates.associateBy { it.id })
        .optional()

    override fun help(context: Context): String = "Initialize a new Amper project based on a template"

    override suspend fun run() {
        val targetRootDir = commonOptions.explicitProjectRoot ?: Path(System.getProperty("user.dir"))
        val selectedTemplate = template ?: promptForTemplate()
        terminal.println("Extracting template ${terminal.theme.info(selectedTemplate.id)} to $targetRootDir…")

        selectedTemplate.extractTo(outputDir = targetRootDir)
        val wrappersGenerated = generateWrapperScripts(targetRootDir)

        printSuccessfulCommandConclusion("Project successfully generated")

        if (wrappersGenerated) {
            terminal.println()
            val buildCommand = if (OsFamily.current.isWindows) "amper.bat build" else "./amper build"
            terminal.println(
                "Now you may build your project with ${terminal.theme.info(buildCommand)} or open this folder in an " +
                        "IDE with the Amper plugin"
            )
        }
    }

    private fun promptForTemplate(): AmperProjectTemplate = terminal.interactiveSelectList(
        title = "Select a project template:",
        items = AmperProjectTemplates.availableTemplates,
        nameSelector = { terminal.theme.info.invoke(it.name) },
        descriptionSelector = { it.description.prependIndent("  ") },
    ) ?: throw PrintMessage("No template selected, project generation aborted")

    private fun AmperProjectTemplate.extractTo(outputDir: Path) {
        val files = listFiles()
        checkTemplateFilesConflicts(files, outputDir)
        outputDir.createDirectories()
        files.forEach {
            it.extractTo(outputDir)
        }
    }

    private fun generateWrapperScripts(targetRootDir: Path): Boolean {
        val sha256 = System.getProperty("amper.wrapper.dist.sha256")
        if (sha256.isNullOrEmpty()) {
            logger.warn("Amper was not run from amper wrapper, skipping generating wrappers for $targetRootDir")
            return false
        }
        AmperWrappers.generate(targetRootDir, amperVersion = AmperBuild.mavenVersion, amperDistTgzSha256 = sha256)
        return true
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
}
