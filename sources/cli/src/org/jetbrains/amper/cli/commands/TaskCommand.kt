/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.cli.commands

import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.terminal
import com.github.ajalt.clikt.parameters.arguments.argument
import org.jetbrains.amper.cli.withBackend
import org.jetbrains.amper.frontend.TaskName

internal class TaskCommand : AmperSubcommand(name = "task") {

    private val name by argument(help = "task name to execute")

    override fun help(context: Context): String = "Execute any task from the task graph"

    override suspend fun run() {
        withBackend(commonOptions, commandName, terminal) { backend ->
            backend.runTask(TaskName(name))
        }
    }
}
