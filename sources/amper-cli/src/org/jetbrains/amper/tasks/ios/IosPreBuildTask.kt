/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.tasks.ios

import kotlinx.serialization.Serializable
import org.jetbrains.amper.engine.Task
import org.jetbrains.amper.engine.TaskGraphExecutionContext
import org.jetbrains.amper.engine.requireSingleDependency
import org.jetbrains.amper.frontend.TaskName
import org.jetbrains.amper.serialization.paths.SerializablePath
import org.jetbrains.amper.tasks.TaskResult
import org.jetbrains.amper.tasks.native.NativeLinkTask

class IosPreBuildTask(
    override val taskName: TaskName,
) : Task {
    override suspend fun run(dependenciesResult: List<TaskResult>, executionContext: TaskGraphExecutionContext): TaskResult {
        val frameworkPath = checkNotNull(
            dependenciesResult.requireSingleDependency<NativeLinkTask.Result>().linkedBinary
        ) { "Framework must always be linked" }

        val composeResourcesPath = dependenciesResult
            .filterIsInstance<IosComposeResourcesTask.Result>()
            .firstOrNull()?.composeResourcesDirectory

        return Result(
            appFrameworkPath = frameworkPath,
            composeResourcesDirectoryPath = composeResourcesPath,
        )
    }

    @Serializable
    class Result(
        val appFrameworkPath: SerializablePath,
        val composeResourcesDirectoryPath: SerializablePath?,
    ) : TaskResult {
        companion object {
            /**
             * If set, signals the integration command that there is a super Amper call and all the necessary tasks have
             * been run.
             */
            const val ENV_JSON_NAME = "AMPER_XCI_INFO_JSON"
        }
    }
}