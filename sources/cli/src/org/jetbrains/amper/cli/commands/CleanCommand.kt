/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
        withBackend(commonOptions, commandName, terminal, setupEnvironment = false) { backend ->
            val rootsToClean = listOf(backend.context.buildOutputRoot.path, backend.context.projectTempRoot.path)
            for (path in rootsToClean) {
                if (path.exists()) {
                    @Suppress("ReplacePrintlnWithLogging")
                    println("Deleting $path")

                    path.deleteRecursively()
                }
            }
        }
    }
}
