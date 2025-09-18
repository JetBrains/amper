/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.tasks.android

import org.jetbrains.amper.android.AndroidBuildRequest
import org.jetbrains.amper.cli.AmperBuildLogsRoot
import org.jetbrains.amper.cli.AmperProjectRoot
import org.jetbrains.amper.frontend.AmperModule
import org.jetbrains.amper.frontend.Fragment
import org.jetbrains.amper.frontend.TaskName
import org.jetbrains.amper.incrementalcache.IncrementalCache
import org.jetbrains.amper.tasks.AdditionalClasspathProvider
import org.jetbrains.amper.tasks.ResolveExternalDependenciesTask
import org.jetbrains.amper.tasks.TaskOutputRoot
import org.jetbrains.amper.tasks.TaskResult
import org.jetbrains.amper.util.BuildType
import java.nio.file.Path
import kotlin.io.path.extension

class AndroidPrepareTask(
    override val taskName: TaskName,
    module: AmperModule,
    buildType: BuildType,
    incrementalCache: IncrementalCache,
    androidSdkPath: Path,
    fragments: List<Fragment>,
    projectRoot: AmperProjectRoot,
    taskOutputRoot: TaskOutputRoot,
    buildLogsRoot: AmperBuildLogsRoot,
) : AndroidDelegatedGradleTask(
    module,
    buildType,
    incrementalCache,
    androidSdkPath,
    fragments,
    projectRoot,
    taskOutputRoot,
    buildLogsRoot,
    taskName
) {
    override val phase: AndroidBuildRequest.Phase
        get() = AndroidBuildRequest.Phase.Prepare

    override fun outputFilterPredicate(path: Path): Boolean = path.extension == "jar"
    override fun result(artifacts: List<Path>): TaskResult = Result(compileClasspath = artifacts)

    override fun runtimeClasspath(dependenciesResult: List<TaskResult>): List<Path> =
        dependenciesResult.filterIsInstance<ResolveExternalDependenciesTask.Result>().flatMap { it.runtimeClasspath }

    class Result(override val compileClasspath: List<Path>) : TaskResult, AdditionalClasspathProvider
}
