/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.cli.commands

import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.terminal
import org.jetbrains.amper.cli.withBackend
import kotlin.io.path.deleteRecursively
import kotlin.io.path.exists

internal class CleanCommand : AmperSubcommand(name = "clean") {

    override fun help(context: Context): String = "Remove the project's build output and caches"

    override suspend fun run() {
        val cliContext = createCliProjectContext()
        val buildDir = cliContext.buildOutputRoot.path
        if (buildDir.exists()) {
            terminal.println("Deleting project build output and caches…")
            buildDir.deleteRecursively()
        }
        printSuccessfulCommandConclusion("Clean successful")
    }
}
