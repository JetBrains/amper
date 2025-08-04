/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.cli.commands

import org.jetbrains.amper.cli.CliContext
import org.jetbrains.amper.plugins.preparePlugins

/**
 * Command to be invoked by the tooling (IDE) to ensure the plugin information is valid and ready.
 */
internal class PreparePluginsCommand : AmperProjectAwareCommand(name = "prepare-plugins") {
    override val hiddenFromHelp: Boolean
        get() = true

    override suspend fun run(cliContext: CliContext) {
        preparePlugins(cliContext)
    }
}
