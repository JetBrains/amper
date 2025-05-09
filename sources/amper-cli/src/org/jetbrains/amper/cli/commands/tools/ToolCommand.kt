/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.cli.commands.tools

import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.subcommands
import org.jetbrains.amper.cli.commands.AmperSubcommand

internal class ToolCommand : AmperSubcommand(name = "tool") {

    init {
        subcommands(
            JaegerToolCommand(),
            JdkToolCommand(),
            KeystoreToolCommand(),
            XCodeIntegrationCommand(),
        )
    }

    override fun help(context: Context): String = "Run a tool"

    override suspend fun run() = Unit
}
