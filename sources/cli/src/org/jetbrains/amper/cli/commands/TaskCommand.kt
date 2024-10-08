/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.cli.commands

import com.github.ajalt.clikt.command.SuspendingCliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.requireObject
import com.github.ajalt.clikt.parameters.arguments.argument
import org.jetbrains.amper.cli.withBackend
import org.jetbrains.amper.frontend.TaskName

internal class TaskCommand : SuspendingCliktCommand(name = "task") {

    val name by argument(help = "task name to execute")

    val commonOptions by requireObject<RootCommand.CommonOptions>()

    override fun help(context: Context): String = "Execute any task from the task graph"

    override suspend fun run() {
        withBackend(commonOptions, commandName) { backend ->
            backend.runTask(TaskName(name))
        }
    }
}
