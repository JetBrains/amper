/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.cli.options

import com.github.ajalt.clikt.core.BaseCliktCommand
import com.github.ajalt.clikt.core.ParameterHolder
import com.github.ajalt.clikt.core.PrintMessage
import com.github.ajalt.clikt.core.terminal
import com.github.ajalt.clikt.parameters.groups.MutuallyExclusiveOptions
import com.github.ajalt.clikt.parameters.groups.default
import com.github.ajalt.clikt.parameters.groups.mutuallyExclusiveOptions
import com.github.ajalt.clikt.parameters.groups.single
import com.github.ajalt.clikt.parameters.options.convert
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.transformAll
import com.github.ajalt.mordant.input.interactiveMultiSelectList
import com.github.ajalt.mordant.terminal.Terminal
import org.jetbrains.amper.cli.userReadableError
import org.jetbrains.amper.frontend.AmperModule
import kotlin.collections.map

internal sealed class ModuleFilter {
    data object All : ModuleFilter()
    data class Names(val moduleNames: Set<String>) : ModuleFilter()
    data object Unspecified : ModuleFilter()
}

internal const val ModuleOptionName = "--module"
internal const val AllModulesOptionName = "--all-modules"

/**
 * A CLI option group to select modules to inspect.
 *
 * @param moduleOptionHelp The help text for the repeatable option to select one module
 * @param allModulesOptionHelp The help text for the flag to select all modules
 */
internal fun ParameterHolder.moduleFilter(
    moduleOptionHelp: String,
    allModulesOptionHelp: String,
): MutuallyExclusiveOptions<ModuleFilter, ModuleFilter> = mutuallyExclusiveOptions(
    option("-m", "--module", help = moduleOptionHelp)
        .transformAll { ModuleFilter.Names(it.toSet()) },
    option("-a", "--all-modules", help = allModulesOptionHelp)
        .flag(default = false)
        .convert { if (it) ModuleFilter.All else null },
).single().default(ModuleFilter.Unspecified)

/**
 * Filters the modules chosen by the user based on this [ModuleFilter].
 *
 * If the filter is [ModuleFilter.Unspecified], the user is prompted to choose modules if there is more than one in the
 * project. If the terminal is not interactive, a user error is shown instead.
 */
context(command: BaseCliktCommand<*>)
internal fun ModuleFilter.selectModules(projectModules: List<AmperModule>): List<AmperModule> =
    selectModules(projectModules, command.terminal)

/**
 * Filters the modules chosen by the user based on this [ModuleFilter].
 *
 * If the filter is [ModuleFilter.Unspecified], the user is prompted to choose modules if there is more than one in the
 * project. If the terminal is not interactive, a user error is shown instead.
 */
internal fun ModuleFilter.selectModules(projectModules: List<AmperModule>, terminal: Terminal): List<AmperModule> = when (this) {
    is ModuleFilter.All -> projectModules
    is ModuleFilter.Names -> filterModulesByName(projectModules)
    is ModuleFilter.Unspecified -> when {
        projectModules.size <= 1 -> projectModules
        terminal.terminalInfo.interactive -> terminal.promptForModules(projectModules)
        else -> userReadableError("Please specify the module(s) to inspect with $ModuleOptionName, or use $AllModulesOptionName to inspect all modules")
    }
}

private fun ModuleFilter.Names.filterModulesByName(projectModules: List<AmperModule>): List<AmperModule> {
    val knownModuleNames = projectModules.mapTo(mutableSetOf()) { it.userReadableName }
    val invalidModuleNames = moduleNames - knownModuleNames
    if (invalidModuleNames.isNotEmpty()) {
        userReadableError("Couldn't find module(s) named: ${invalidModuleNames.joinToString()}\n" +
                "Available modules: ${knownModuleNames.sorted().joinToString("\n") { "- $it" }}")
    }
    return projectModules.filter { it.userReadableName in moduleNames }
}

private fun Terminal.promptForModules(availableModules: List<AmperModule>): List<AmperModule> {
    val selectedModules = promptForModuleNames(availableModuleNames = availableModules.map { it.userReadableName })
    return availableModules.filter { it.userReadableName in selectedModules }
}

private fun Terminal.promptForModuleNames(availableModuleNames: List<String>): List<String> {
    var selectedModules: List<String>
    do {
        selectedModules = interactiveMultiSelectList {
            title("Please select at least one module you want to inspect using ${theme.info("[x]")}, and confirm with ${theme.info("[Enter]")}:")
            entries(availableModuleNames)
            filterable(true)
        } ?: throw PrintMessage("Command aborted.")
    } while (selectedModules.isEmpty())
    return selectedModules
}
