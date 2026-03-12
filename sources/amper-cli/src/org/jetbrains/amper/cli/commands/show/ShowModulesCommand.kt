/*
 * Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.cli.commands.show

import com.github.ajalt.clikt.completion.CompletionCandidates
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.terminal
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.transformAll
import com.github.ajalt.clikt.parameters.types.enum
import com.github.ajalt.mordant.markdown.Markdown
import com.github.ajalt.mordant.rendering.BorderType
import com.github.ajalt.mordant.table.table
import org.jetbrains.amper.cli.CliContext
import org.jetbrains.amper.cli.commands.AmperModelAwareCommand
import org.jetbrains.amper.frontend.AmperModule
import org.jetbrains.amper.frontend.Model

private enum class ModulesListFormat(val cliName: String) {
    Plain("plain"),
    Table("table"),
}

private enum class ModuleField(val cliName: String, val description: String) {
    Name("name", "the name of the module"),
    Type("type", "the product type of the module"),
    Platforms("platforms", "the platforms supported by the module"),
    ShortDesc("shortdesc", "the first line of the module's description"),
    Description("description", "the full description of the module");

    companion object {
        val defaultList = listOf(Name, Type, ShortDesc)
    }
}

internal class ShowModulesCommand : AmperModelAwareCommand(name = "modules") {

    private val format by option(
        "--format",
        help = "The format of the output. Available formats:\n" +
                " - `plain`: plain list of module names\n" +
                " - `table`: formatted table with multiple columns " +
                "(use `--fields` to customize the columns to show)\n\n",
    )
        .enum<ModulesListFormat> { it.cliName }
        .default(ModulesListFormat.Table, defaultForHelp = "table")

    private val fields by option(
        "--fields",
        help = "A comma-separated list of fields to show as columns in the `table` format (ignored when using the " +
                "`plain` format). Available fields:\n" +
                "${ModuleField.entries.joinToString("\n") { " - `${it.cliName}`: ${it.description}"}}\n\n",
        metavar = "field(,field)*",
        completionCandidates = CompletionCandidates.Fixed(ModuleField.entries.map { it.cliName }.toSet()),
    )
        .transformAll(
            defaultForHelp = ModuleField.defaultList.joinToString(",") { it.cliName},
        ) { fieldGroups ->
            fieldGroups.flatMap { fieldGroup ->
                fieldGroup.split(",").map { field ->
                    ModuleField.entries.find { it.cliName == field }
                        ?: error("Invalid field: $field, must be one of ${ModuleField.entries.map { it.cliName }}")
                }
            }.ifEmpty { ModuleField.defaultList }
        }

    override fun help(context: Context): String = "List all modules in the project"

    override suspend fun run(cliContext: CliContext, model: Model) {
        val modules = model.modules.sortedBy { it.userReadableName }
        when (format) {
            ModulesListFormat.Plain -> printPlainModulesList(modules)
            ModulesListFormat.Table -> printModulesTable(modules)
        }
    }

    private fun printPlainModulesList(modules: List<AmperModule>) {
        modules.forEach {
            terminal.println(it.userReadableName)
        }
    }

    private fun printModulesTable(modules: List<AmperModule>) {
        val modulesTable = table {
            borderType = BorderType.ROUNDED
            header {
                row {
                    fields.forEach {
                        when (it) {
                            ModuleField.Name -> cell(Markdown("**Name**"))
                            ModuleField.Type -> cell(Markdown("**Type**"))
                            ModuleField.Platforms -> cell(Markdown("**Platforms**"))
                            ModuleField.ShortDesc -> cell(Markdown("**Short description**"))
                            ModuleField.Description -> cell(Markdown("**Description**"))
                        }
                    }
                }
            }
            body {
                modules.forEach { module ->
                    row {
                        fields.forEach {
                            when (it) {
                                ModuleField.Name -> cell(terminal.theme.info(module.userReadableName))
                                ModuleField.Type -> cell(module.type.value)
                                ModuleField.Platforms -> cell(module.leafPlatforms.joinToString("\n"))
                                ModuleField.ShortDesc -> cell(Markdown(module.description?.substringBefore('\n') ?: ""))
                                ModuleField.Description -> cell(Markdown(module.description ?: ""))
                            }
                        }
                    }
                }
            }
        }
        terminal.println(modulesTable)
    }
}
