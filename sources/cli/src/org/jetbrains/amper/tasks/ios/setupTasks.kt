/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.tasks.ios

import org.jetbrains.amper.cli.TaskGraphBuilder
import org.jetbrains.amper.engine.TaskName
import org.jetbrains.amper.frontend.Platform
import org.jetbrains.amper.frontend.PotatoModule
import org.jetbrains.amper.tasks.TaskOutputRoot
import org.jetbrains.amper.util.BuildType
import org.jetbrains.amper.util.ExecuteOnChangedInputs


/**
 * Setup apple related tasks.
 */
fun TaskGraphBuilder.setupAppleTasks(
    buildTaskName: TaskName,
    runTaskName: TaskName,
    module: PotatoModule,
    platform: Platform,
    buildType: BuildType,
    executeOnChangedInputs: ExecuteOnChangedInputs,
    dependsOn: List<TaskName>,
    taskOutput: (TaskName) -> TaskOutputRoot,
): TaskName {
    registerTask(
        task = BuildAppleTask(
            platform,
            module,
            buildType,
            executeOnChangedInputs,
            taskOutput(buildTaskName),
            buildTaskName,
        ),
        dependsOn = dependsOn
    )

    registerTask(
        task = RunAppleTask(
            runTaskName,
            taskOutput(runTaskName),
        ),
        dependsOn = listOf(buildTaskName)
    )
    return buildTaskName
}
