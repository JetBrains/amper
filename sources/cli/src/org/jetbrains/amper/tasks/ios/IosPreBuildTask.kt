/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.tasks.ios

import org.jetbrains.amper.BuildPrimitives
import org.jetbrains.amper.cli.AmperBuildOutputRoot
import org.jetbrains.amper.engine.Task
import org.jetbrains.amper.engine.requireSingleDependency
import org.jetbrains.amper.frontend.AmperModule
import org.jetbrains.amper.frontend.Platform
import org.jetbrains.amper.frontend.TaskName
import org.jetbrains.amper.tasks.EmptyTaskResult
import org.jetbrains.amper.tasks.TaskResult
import org.jetbrains.amper.tasks.native.NativeLinkTask
import org.jetbrains.amper.util.BuildType
import kotlin.io.path.createParentDirectories

class IosPreBuildTask(
    override val taskName: TaskName,
    private val module: AmperModule,
    private val buildType: BuildType,
    private val platform: Platform,
    private val outputRoot: AmperBuildOutputRoot,
) : Task {
    override suspend fun run(dependenciesResult: List<TaskResult>): TaskResult {
        val frameworkPath = dependenciesResult.requireSingleDependency<NativeLinkTask.Result>().linkedBinary

        IosConventions.Context(
            buildRootPath = outputRoot.path,
            moduleName = module.userReadableName,
            buildType = buildType,
            platform = platform,
        ).run {
            val targetPath = IosConventions.getAppFrameworkPath()
            targetPath.createParentDirectories()
            BuildPrimitives.copy(
                from = frameworkPath,
                to = targetPath,
                overwrite = true,  // TODO: incremental instead?
            )
        }

        return EmptyTaskResult
    }
}