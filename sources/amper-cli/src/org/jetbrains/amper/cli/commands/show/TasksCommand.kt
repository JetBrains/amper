/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.cli.commands.show

import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.terminal
import org.jetbrains.amper.cli.CliContext
import org.jetbrains.amper.cli.commands.AmperProjectAwareCommand
import org.jetbrains.amper.cli.withBackend

internal class TasksCommand : AmperProjectAwareCommand(name = "tasks") {

    override fun help(context: Context): String = "List all tasks in the project and their dependencies"

    override suspend fun run(cliContext: CliContext) {
        val taskGraph = withBackend(cliContext) { backend -> backend.taskGraph }

        for (taskName in taskGraph.tasks.map { it.taskName }.sortedBy { it.name }) {
            val taskWithDeps = buildString {
                append("task ${taskName.name}")
                taskGraph.dependencies[taskName]?.let { taskDeps ->
                    append(" -> ${taskDeps.joinToString { it.name }}")
                }
            }
            terminal.println(taskWithDeps)
        }
    }
}
