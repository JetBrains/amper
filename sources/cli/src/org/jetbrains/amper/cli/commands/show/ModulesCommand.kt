/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.cli.commands.show

import com.github.ajalt.clikt.core.Context
import org.jetbrains.amper.cli.commands.AmperSubcommand
import org.jetbrains.amper.cli.withBackend

internal class ModulesCommand : AmperSubcommand(name = "modules") {

    override fun help(context: Context): String = "List all modules in the project"

    override suspend fun run() {
        withBackend(commonOptions, commandName) { backend ->
            backend.showModules()
        }
    }
}
