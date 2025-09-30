/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.tasks.android

import org.jetbrains.amper.android.AndroidBuildRequest
import org.jetbrains.amper.cli.AmperBuildLogsRoot
import org.jetbrains.amper.cli.AmperProjectRoot
import org.jetbrains.amper.core.AmperUserCacheRoot
import org.jetbrains.amper.frontend.AmperModule
import org.jetbrains.amper.frontend.Fragment
import org.jetbrains.amper.frontend.TaskName
import org.jetbrains.amper.incrementalcache.IncrementalCache
import org.jetbrains.amper.tasks.ResolveExternalDependenciesTask
import org.jetbrains.amper.tasks.TaskOutputRoot
import org.jetbrains.amper.tasks.TaskResult
import org.jetbrains.amper.tasks.jvm.RuntimeClasspathElementProvider
import org.jetbrains.amper.util.BuildType
import java.nio.file.Path
import kotlin.io.path.extension

class AndroidMockablePlatformJarTask(
    override val taskName: TaskName,
    module: AmperModule,
    buildType: BuildType,
    incrementalCache: IncrementalCache,
    androidSdkPath: Path,
    fragments: List<Fragment>,
    projectRoot: AmperProjectRoot,
    taskOutputRoot: TaskOutputRoot,
    buildLogsRoot: AmperBuildLogsRoot,
    userCacheRoot: AmperUserCacheRoot,
) : AndroidDelegatedGradleTask(
    module,
    buildType,
    incrementalCache,
    androidSdkPath,
    fragments,
    projectRoot,
    taskOutputRoot,
    buildLogsRoot,
    userCacheRoot,
    taskName,
) {
    override val phase: AndroidBuildRequest.Phase
        get() = AndroidBuildRequest.Phase.Test

    override fun outputFilterPredicate(path: Path): Boolean = path.extension == "jar"
    override fun result(artifacts: List<Path>): TaskResult = Result(paths = artifacts)

    override fun runtimeClasspath(dependenciesResult: List<TaskResult>): List<Path> {
        return dependenciesResult.filterIsInstance<ResolveExternalDependenciesTask.Result>().flatMap { it.runtimeClasspath }
    }

    class Result(override val paths: List<Path>) : TaskResult, RuntimeClasspathElementProvider
}
