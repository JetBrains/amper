/*
 * Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.cli.commands

import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.multiple
import org.jetbrains.amper.cli.CliContext
import org.jetbrains.amper.cli.withBackend
import org.jetbrains.amper.frontend.Model
import org.jetbrains.amper.frontend.TaskName

internal class TaskCommand : AmperModelAwareCommand(name = "task") {

    private val taskNames by argument(
        help = "The name of the task to run. This option can be repeated to run multiple tasks. In this case, the tasks are executed in parallel.",
    ).multiple(required = true)

    override fun help(context: Context): String = "Run a task and its dependencies from the task graph"

    override suspend fun run(cliContext: CliContext, model: Model) {
        withBackend(cliContext, model) { backend ->
            backend.runTasks(taskNames.map { TaskName(it) }.toSet())
        }
    }
}
