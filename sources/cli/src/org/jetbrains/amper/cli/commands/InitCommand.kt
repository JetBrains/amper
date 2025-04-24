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
import com.github.ajalt.mordant.input.interactiveSelectList
import com.github.ajalt.mordant.widgets.SelectList
import org.jetbrains.amper.generator.ProjectGenerator
import org.jetbrains.amper.templates.AmperProjectTemplate
import org.jetbrains.amper.templates.AmperProjectTemplates
import org.jetbrains.amper.util.filterAnsiCodes
import kotlin.io.path.Path

internal class InitCommand : AmperSubcommand(name = "init") {

    private val template by argument(help = "the name of a project template (leave blank to select interactively from a list)")
        .choice(AmperProjectTemplates.availableTemplates.associateBy { it.name })
        .optional()

    override fun help(context: Context): String = "Initialize a new Amper project based on a template"

    override suspend fun run() {
        val directory = commonOptions.explicitRoot ?: Path(System.getProperty("user.dir"))
        ProjectGenerator(terminal = terminal).initProject(
            template = template ?: promptForTemplate(),
            directory = directory,
        )
    }

    private fun promptForTemplate(): AmperProjectTemplate {
        val choice = terminal.interactiveSelectList(
            title = "Select a project template:",
            entries = AmperProjectTemplates.availableTemplates.map {
                SelectList.Entry(
                    title = terminal.theme.info.invoke(it.name),
                    description = it.description.prependIndent("  "),
                )
            },
        )
        if (choice == null) {
            throw PrintMessage("No template selected, project generation aborted")
        }
        val selectedTemplateName = choice.filterAnsiCodes()
        return AmperProjectTemplates.availableTemplates.firstOrNull { it.name == selectedTemplateName }
            ?: error("Template with name '$selectedTemplateName' not found")
    }
}
