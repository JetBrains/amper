/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.tasks

import AndroidBuildRequest
import RClassAndroidBuildResult
import org.jetbrains.amper.cli.TaskName
import org.jetbrains.amper.frontend.PotatoModule
import org.jetbrains.amper.frontend.PotatoModuleFileSource
import runAndroidBuild

class AndroidPrepareTask(private val module: PotatoModule, override val taskName: TaskName): Task {
    override suspend fun run(dependenciesResult: List<TaskResult>): TaskResult {
        val rootPath = (module.source as? PotatoModuleFileSource)?.buildFile ?: error("No build file ${module.source}")
        // get dependencies
//        val request = AndroidBuildRequest(rootPath, AndroidBuildRequest.Phase.Prepare, )
//        runAndroidBuild<RClassAndroidBuildResult>(request)
        TODO("Not yet implemented")
    }
}