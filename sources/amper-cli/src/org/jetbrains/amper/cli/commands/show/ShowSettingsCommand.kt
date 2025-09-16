/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.cli.commands.show

import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.terminal
import com.github.ajalt.mordant.rendering.TextAlign
import com.github.ajalt.mordant.rendering.Whitespace
import com.github.ajalt.mordant.terminal.info
import org.jetbrains.amper.cli.CliContext
import org.jetbrains.amper.cli.commands.AmperModelAwareCommand
import org.jetbrains.amper.cli.options.AllModulesOptionName
import org.jetbrains.amper.cli.options.moduleFilter
import org.jetbrains.amper.cli.options.selectModules
import org.jetbrains.amper.frontend.Fragment
import org.jetbrains.amper.frontend.Model
import org.jetbrains.amper.frontend.schema.ProductType
import org.jetbrains.amper.frontend.serialization.YamlTheme
import org.jetbrains.amper.frontend.serialization.serializeAsAmperYaml

internal class ShowSettingsCommand : AmperModelAwareCommand(name = "settings") {

    private val moduleFilter by moduleFilter(
        moduleOptionHelp = """
            The module to show the settings of (run the `show modules` command to get the modules list).
            This option can be repeated to show settings for several modules.
            If unspecified, you will be prompted to choose one or more modules.

            See also `$AllModulesOptionName` if you want to show settings for all modules in the project.
        """.trimIndent(),
        allModulesOptionHelp = "Show settings for all modules.",
    )

    override fun help(context: Context): String = "Print the effective Amper settings of each module"

    override suspend fun run(cliContext: CliContext, model: Model) {
        val isMultimodule = model.modules.size
        moduleFilter.selectModules(model.modules).forEach { module ->
            if (isMultimodule > 1) {
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
            "settings@${fragment.platforms.joinToString("+") { it.pretty }}:"
        } else {
            "settings:"
        }
        terminal.info(settingsNodeName)
        val yamlLikeEffectiveSettings = fragment.settings.serializeAsAmperYaml(
            productType = productType,
            contexts = fragment.platforms,
            theme = object : YamlTheme {
                override fun colorize(text: String, type: YamlTheme.SpecialElementType): String = when (type) {
                    YamlTheme.SpecialElementType.Comment -> terminal.theme.muted(text)
                    YamlTheme.SpecialElementType.PropertyName -> terminal.theme.info(text)
                }
            }
        )
        terminal.println(yamlLikeEffectiveSettings.prependIndent("  "))
        terminal.println()
    }
}
