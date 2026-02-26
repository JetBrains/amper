/*
 * Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.cli.commands.show

import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.terminal
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import org.jetbrains.amper.cli.CliContext
import org.jetbrains.amper.cli.commands.AmperModelAwareCommand
import org.jetbrains.amper.frontend.AmperModule
import org.jetbrains.amper.frontend.Model

internal class ShowModulesCommand : AmperModelAwareCommand(name = "modules") {

    // Maybe we could introduce a general --format option with variables so users can fully customize the output
    private val showDescription by option("--show-description", help = "Include the module descriptions in the output")
        .flag("--no-description", default = true, defaultForHelp = "show")

    override fun help(context: Context): String = "List all modules in the project"

    override suspend fun run(cliContext: CliContext, model: Model) {
        for (module in model.modules.sortedBy { it.userReadableName }) {
            terminal.println(formatLine(module))
        }
    }

    private fun formatLine(module: AmperModule): String {
        val coloredName = terminal.theme.info(module.userReadableName)
        return if (module.description.isNullOrBlank() || !showDescription) {
            coloredName
        } else {
            "$coloredName:\t${module.description}"
        }
    }
}
