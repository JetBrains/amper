/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.cli.commands

import org.jetbrains.amper.cli.logging.LoggingInitializer
import org.jetbrains.amper.cli.telemetry.TelemetryEnvironment
import org.jetbrains.amper.plugins.preparePlugins

/**
 * Command to be invoked by the tooling (IDE) to ensure the plugin information is valid and ready.
 */
internal class PreparePluginsCommand : AmperSubcommand(name = "prepare-plugins") {
    override val hiddenFromHelp: Boolean
        get() = true

    override suspend fun run() {
        val context = createCliProjectContext()
        TelemetryEnvironment.setLogsRootDirectory(context.currentLogsRoot)
        LoggingInitializer.setupFileLogging(context.currentLogsRoot)

        preparePlugins(context)
    }
}

