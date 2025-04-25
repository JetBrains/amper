/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.cli.commands

import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.terminal
import com.github.ajalt.clikt.parameters.arguments.argument
import org.jetbrains.amper.cli.withBackend
import org.jetbrains.amper.frontend.TaskName

internal class TaskCommand : AmperSubcommand(name = "task") {

    private val name by argument(help = "The name of the task to run")

    override fun help(context: Context): String = "Run a task and its dependencies from the task graph"

    override suspend fun run() {
        withBackend(commonOptions, commandName, terminal) { backend ->
            backend.runTask(TaskName(name))
        }
    }
}
