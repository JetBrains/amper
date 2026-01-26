/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.tasks.jvm

import com.github.ajalt.mordant.terminal.Terminal
import org.jetbrains.amper.ProcessRunner
import org.jetbrains.amper.cli.AmperProjectRoot
import org.jetbrains.amper.cli.AmperProjectTempRoot
import org.jetbrains.amper.core.AmperUserCacheRoot
import org.jetbrains.amper.frontend.AmperModule
import org.jetbrains.amper.frontend.TaskName
import org.jetbrains.amper.incrementalcache.IncrementalCache
import org.jetbrains.amper.jdk.provisioning.JdkProvider
import org.jetbrains.amper.tasks.JvmMainRunSettings
import org.jetbrains.amper.util.BuildType

class JvmRunTask(
    taskName: TaskName,
    module: AmperModule,
    userCacheRoot: AmperUserCacheRoot,
    projectRoot: AmperProjectRoot,
    tempRoot: AmperProjectTempRoot,
    terminal: Terminal,
    runSettings: JvmMainRunSettings,
    incrementalCache: IncrementalCache,
    jdkProvider: JdkProvider,
    processRunner: ProcessRunner,
) : AbstractJvmRunTask(
    taskName = taskName,
    module = module,
    userCacheRoot = userCacheRoot,
    projectRoot = projectRoot,
    tempRoot = tempRoot,
    terminal = terminal,
    runSettings = runSettings,
    incrementalCache = incrementalCache,
    jdkProvider = jdkProvider,
    processRunner = processRunner
) {
    override val buildType: BuildType = BuildType.Debug
}
