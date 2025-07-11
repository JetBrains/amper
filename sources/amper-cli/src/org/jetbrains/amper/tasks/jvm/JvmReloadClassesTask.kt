/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.tasks.jvm

import org.jetbrains.amper.composehotreload.recompiler.ENV_COMPOSE_HOT_RELOAD_ORCHESTRATION_PORT
import org.jetbrains.amper.engine.Task
import org.jetbrains.amper.engine.TaskGraphExecutionContext
import org.jetbrains.amper.frontend.TaskName
import org.jetbrains.amper.incrementalcache.ExecuteOnChangedInputs
import org.jetbrains.amper.tasks.EmptyTaskResult
import org.jetbrains.amper.tasks.TaskResult
import org.jetbrains.compose.reload.core.getOrThrow
import org.jetbrains.compose.reload.orchestration.OrchestrationClientRole
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage.ReloadClassesRequest
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage.ReloadClassesRequest.ChangeType
import org.jetbrains.compose.reload.orchestration.connectOrchestrationClient
import org.jetbrains.compose.reload.orchestration.sendBlocking
import org.slf4j.LoggerFactory
import java.io.File

val logger = LoggerFactory.getLogger("JvmReloadClassesTask")

class JvmReloadClassesTask(override val taskName: TaskName) : Task {
    override suspend fun run(
        dependenciesResult: List<TaskResult>,
        executionContext: TaskGraphExecutionContext,
    ): TaskResult {

        val orchestrationPort = System.getenv(ENV_COMPOSE_HOT_RELOAD_ORCHESTRATION_PORT)
            ?: error("$ENV_COMPOSE_HOT_RELOAD_ORCHESTRATION_PORT environment variable is not set")

        val port = try {
            orchestrationPort.toInt()
        } catch (_: NumberFormatException) {
            error("$ENV_COMPOSE_HOT_RELOAD_ORCHESTRATION_PORT environment variable has incorrect value: $orchestrationPort")
        }

        val changes = dependenciesResult.filterIsInstance<JvmCompileTask.Result>().flatMap { it.changes }

        connectOrchestrationClient(OrchestrationClientRole.Compiler, port).getOrThrow().use { client ->
            val changedClassFiles = mutableMapOf<File, ChangeType>()
            changes.forEach { change ->
                val changeType = when (change.type) {
                    ExecuteOnChangedInputs.Change.ChangeType.CREATED -> ChangeType.Added
                    ExecuteOnChangedInputs.Change.ChangeType.MODIFIED -> ChangeType.Modified
                    ExecuteOnChangedInputs.Change.ChangeType.DELETED -> ChangeType.Removed
                }
                changedClassFiles[change.path.toAbsolutePath().toFile()] = changeType
            }
            logger.info("Sending reload classes request to the IDE: $changedClassFiles")
            client.sendBlocking(ReloadClassesRequest(changedClassFiles))
        }

        return EmptyTaskResult
    }
}
