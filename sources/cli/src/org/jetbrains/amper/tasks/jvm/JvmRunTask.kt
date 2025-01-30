/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.tasks.jvm

import com.github.ajalt.mordant.terminal.Terminal
import org.jetbrains.amper.cli.AmperProjectRoot
import org.jetbrains.amper.cli.AmperProjectTempRoot
import org.jetbrains.amper.core.AmperUserCacheRoot
import org.jetbrains.amper.frontend.AmperModule
import org.jetbrains.amper.frontend.TaskName
import org.jetbrains.amper.incrementalcache.ExecuteOnChangedInputs
import org.jetbrains.amper.jvm.JdkDownloader
import org.jetbrains.amper.tasks.CommonRunSettings
import org.jetbrains.amper.tasks.compose.isComposeEnabledFor
import org.jetbrains.amper.tasks.compose.isHotReloadEnabledFor
import org.jetbrains.amper.util.BuildType
import java.nio.file.Path

class JvmRunTask(
    taskName: TaskName,
    module: AmperModule,
    userCacheRoot: AmperUserCacheRoot,
    projectRoot: AmperProjectRoot,
    tempRoot: AmperProjectTempRoot,
    terminal: Terminal,
    commonRunSettings: CommonRunSettings,
    executeOnChangedInputs: ExecuteOnChangedInputs,
) : AbstractJvmRunTask(
    taskName, 
    module, 
    userCacheRoot, 
    projectRoot, 
    tempRoot, 
    terminal, 
    commonRunSettings, 
    executeOnChangedInputs
) {
    override val buildType: BuildType =
        if (isComposeEnabledFor(module) && isHotReloadEnabledFor(module)) BuildType.Release
        else BuildType.Debug

    override suspend fun jvmArgs(): List<String> {
        // TODO also support options from module files? (AMPER-3253)
        return listOf("-ea") + commonRunSettings.userJvmArgs
    }

    override suspend fun finalClasspath(classpath: List<Path>): List<Path> {
        return classpath
    }

    override suspend fun getJdk(): org.jetbrains.amper.jvm.Jdk {
        return JdkDownloader.getJdk(userCacheRoot)
    }
}
