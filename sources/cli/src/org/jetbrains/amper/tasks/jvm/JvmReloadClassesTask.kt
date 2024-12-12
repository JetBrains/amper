/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.tasks.jvm

import org.jetbrains.amper.engine.Task
import org.jetbrains.amper.frontend.TaskName
import org.jetbrains.amper.tasks.TaskResult
import org.jetbrains.amper.util.ExecuteOnChangedInputs
import org.jetbrains.compose.reload.orchestration.OrchestrationClientRole
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage.ReloadClassesRequest
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage.ReloadClassesRequest.ChangeType
import org.jetbrains.compose.reload.orchestration.connectOrchestrationClient
import java.io.File

private const val ENV_COMPOSE_RELOAD_ORCHESTRATION_PORT = "COMPOSE_HOT_RELOAD_ORCHESTRATION_PORT"

class JvmReloadClassesTask(override val taskName: TaskName) : Task {
    override suspend fun run(dependenciesResult: List<TaskResult>): TaskResult {

        val orchestrationPort = System.getenv(ENV_COMPOSE_RELOAD_ORCHESTRATION_PORT)
            ?: error("$ENV_COMPOSE_RELOAD_ORCHESTRATION_PORT environment variable is not set")

        val port = try {
            orchestrationPort.toInt()
        } catch (_: NumberFormatException) {
            error("$ENV_COMPOSE_RELOAD_ORCHESTRATION_PORT environment variable has incorrect value: $orchestrationPort")
        }

        val changes = dependenciesResult.filterIsInstance<JvmCompileTask.Result>().flatMap { it.changes }

        connectOrchestrationClient(OrchestrationClientRole.Compiler, port).use { client ->
            val changedClassFiles = mutableMapOf<File, ChangeType>()
            changes.forEach { change ->
                val changeType = when (change.type) {
                    ExecuteOnChangedInputs.Change.ChangeType.CREATED -> ChangeType.Added
                    ExecuteOnChangedInputs.Change.ChangeType.MODIFIED -> ChangeType.Modified
                    ExecuteOnChangedInputs.Change.ChangeType.DELETED -> ChangeType.Removed
                }
                changedClassFiles[change.path.toAbsolutePath().toFile()] = changeType
            }
            client.sendMessage(ReloadClassesRequest(changedClassFiles))
        }

        return object : TaskResult {}
    }
}
