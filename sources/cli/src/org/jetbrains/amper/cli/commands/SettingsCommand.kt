/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.cli.commands

import com.github.ajalt.clikt.core.Context
import com.github.ajalt.mordant.rendering.TextAlign
import com.github.ajalt.mordant.rendering.Whitespace
import com.github.ajalt.mordant.terminal.info
import com.github.ajalt.mordant.widgets.Text
import org.jetbrains.amper.cli.CliProblemReporterContext
import org.jetbrains.amper.cli.withBackend
import org.jetbrains.amper.core.get
import org.jetbrains.amper.frontend.aomBuilder.SchemaBasedModelImport
import org.jetbrains.amper.frontend.valueTracking.TracesPresentation
import org.jetbrains.amper.frontend.valueTracking.compositeValueTracesInfo

internal class SettingsCommand: AmperSubcommand(name = "settings") {

    override fun help(context: Context): String = "Print the effective Amper settings of each module"

    override suspend fun run() {
        withBackend(commonOptions, commandName) { backend ->
            val terminal = backend.context.terminal

            val model = with(CliProblemReporterContext) {
                SchemaBasedModelImport.getModel(backend.context.projectContext)
            }.get()

            model.modules.forEach { module ->
                if (model.modules.size > 1) {
                    terminal.info(
                        "Module: " + module.userReadableName + "\n",
                        Whitespace.PRE_LINE, TextAlign.LEFT
                    )
                }
                val distinctSegments = module.fragments.distinctBy { it.platforms }
                distinctSegments.forEach {
                    if (distinctSegments.size > 1) {
                        terminal.info("settings" + it.platforms.joinToString("+") { it.pretty }.let {
                            if (it.isNotEmpty()) "@$it" else it
                        } + "\n", Whitespace.PRE_LINE, TextAlign.LEFT)
                    }
                    compositeValueTracesInfo(
                        it.settings,
                        null,
                        module.type,
                        it.platforms,
                        TracesPresentation.CLI
                    )?.let {
                        // whitespace ahead is necessary for copypastability
                        terminal.print(Text(it.split("\n").joinToString("\n") { "  $it" }))
                    }
                }
            }
        }
    }
}
