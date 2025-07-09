/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.cli.commands

import com.github.ajalt.clikt.core.terminal
import kotlinx.coroutines.coroutineScope
import org.jetbrains.amper.cli.CliContext
import org.jetbrains.amper.cli.logging.LoggingInitializer
import org.jetbrains.amper.cli.telemetry.TelemetryEnvironment
import org.jetbrains.amper.plugins.preparePlugins
import org.jetbrains.amper.tasks.AllRunSettings
import kotlin.io.path.createDirectories

internal class PreparePluginsCommand : AmperSubcommand(name = "prepare-plugins") {
    override val hiddenFromHelp: Boolean
        get() = true

    override suspend fun run() = coroutineScope {
        val context = CliContext.create(
            explicitProjectRoot = commonOptions.explicitRoot?.toAbsolutePath(),
            explicitBuildRoot = commonOptions.buildOutputRoot?.createDirectories()?.toAbsolutePath(),
            userCacheRoot = commonOptions.sharedCachesRoot,
            currentTopLevelCommand = commandName,
            runSettings = AllRunSettings(),
            backgroundScope = this,
            terminal = terminal,
        )
        TelemetryEnvironment.setLogsRootDirectory(context.buildLogsRoot)
        LoggingInitializer.setupFileLogging(context.buildLogsRoot)

        preparePlugins(context)
    }
}

