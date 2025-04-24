/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.cli.commands.show

import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.subcommands
import org.jetbrains.amper.cli.commands.AmperSubcommand

internal class ShowCommand : AmperSubcommand(name = "show") {

    init {
        subcommands(
            ModulesCommand(),
            SettingsCommand(),
            TasksCommand(),
        )
    }

    override fun help(context: Context): String = "Show information about some aspect the project (modules, tasks, effective settings...). See help for details."

    override suspend fun run() = Unit
}
