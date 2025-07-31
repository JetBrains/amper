/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.cli.commands.show

import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.PrintMessage
import com.github.ajalt.clikt.core.terminal
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.multiple
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.unique
import com.github.ajalt.mordant.rendering.TextAlign
import com.github.ajalt.mordant.rendering.Whitespace
import com.github.ajalt.mordant.terminal.info
import org.jetbrains.amper.cli.commands.AmperSubcommand
import org.jetbrains.amper.cli.interactiveMultiSelectList
import org.jetbrains.amper.cli.withBackend
import org.jetbrains.amper.frontend.AmperModule
import org.jetbrains.amper.frontend.Fragment
import org.jetbrains.amper.frontend.schema.ProductType
import org.jetbrains.amper.frontend.valueTracking.TracesPresentation
import org.jetbrains.amper.frontend.valueTracking.compositeValueTracesInfo

internal class SettingsCommand: AmperSubcommand(name = "settings") {

    private val modules by option("-m", "--module",
        help = "The module to show the settings of (run the 'show modules' command to get the modules list). " +
                "This option can be repeated to show settings for several modules. " +
                "If unspecified, modules can be selected from an interactive list (only if there are several modules)."
    ).multiple().unique()

    private val all by option("--all", help = "Show settings for all modules.").flag(default = false)

    override fun help(context: Context): String = "Print the effective Amper settings of each module"

    override suspend fun run() {
        // FIXME we don't need the backend just to get the list of modules, so this should be refactored
        val modules = withBackend(commonOptions, commandName, terminal) { it.modules() }
        modules.filterModulesToInspect().forEach { module ->
            if (modules.size > 1) {
                terminal.info("Module: ${module.userReadableName}\n", Whitespace.PRE_LINE, TextAlign.LEFT)
            }
            module.fragments
                .distinctBy { it.platforms }
                .forEach { fragment ->
                    printFragmentSettings(fragment = fragment, productType = module.type)
                }
        }
    }

    private fun printFragmentSettings(
        fragment: Fragment,
        productType: ProductType,
    ) {
        val settingsNodeName = if (fragment.platforms.isNotEmpty()) {
            "settings@${fragment.platforms.joinToString("+") { it.pretty }}"
        } else {
            "settings"
        }
        terminal.println(settingsNodeName, Whitespace.PRE_LINE, TextAlign.LEFT)
        val yamlLikeEffectiveSettings = compositeValueTracesInfo(
            value = fragment.settings,
            containingFile = null,
            product = productType,
            contexts = fragment.platforms,
            presentation = TracesPresentation.CLI
        ) ?: return
        // whitespace ahead is necessary for copy-pastability
        terminal.println(yamlLikeEffectiveSettings.prependIndent("  "))
    }

    private fun List<AmperModule>.filterModulesToInspect(): List<AmperModule> {
        if (size <= 1 || all) {
            return this
        }
        val selectedModules = modules.takeIf { it.isNotEmpty() } ?: promptForModules(availableModules = this)
        return filter { it in selectedModules }
    }

    private fun promptForModules(availableModules: List<AmperModule>): List<AmperModule> {
        var selectedModules: List<AmperModule>
        do {
            selectedModules = terminal.interactiveMultiSelectList(
                title = "Please select at least one module you want to inspect using ${terminal.theme.info("x")}, and confirm with ${terminal.theme.info("[Enter]")}:",
                items = availableModules,
                nameSelector = { it.userReadableName },
            ) ?: throw PrintMessage("Command aborted.")
        } while (selectedModules.isEmpty())
        return selectedModules
    }
}
