/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.cli.commands

import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.terminal
import org.jetbrains.amper.cli.project.findProjectContext
import org.jetbrains.amper.cli.userReadableError
import kotlin.io.path.deleteRecursively
import kotlin.io.path.exists

// Not an AmperProjectAwareCommand because we don't want to generate logs and telemetry in the build folder.
// This is sort of a global command that cleans the project where it's called.
internal class CleanCommand : AmperSubcommand(name = "clean") {

    override fun help(context: Context): String = "Remove the project's build output and caches"

    override suspend fun run() {
        val projectContext = findProjectContext(
            explicitProjectRoot = commonOptions.explicitProjectRoot,
            explicitBuildRoot = commonOptions.explicitBuildOutputRoot,
        ) ?: userReadableError("No Amper project found, nothing to clean")

        if (projectContext.projectBuildDir.exists()) {
            terminal.println("Deleting project build output and caches…")
            projectContext.projectBuildDir.deleteRecursively()
        }
        printSuccessfulCommandConclusion("Clean successful")
    }
}
