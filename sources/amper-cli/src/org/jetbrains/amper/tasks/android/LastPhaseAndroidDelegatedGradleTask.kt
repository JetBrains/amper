/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.tasks.android

import org.jetbrains.amper.cli.AmperBuildLogsRoot
import org.jetbrains.amper.cli.AmperProjectRoot
import org.jetbrains.amper.core.AmperUserCacheRoot
import org.jetbrains.amper.frontend.AmperModule
import org.jetbrains.amper.frontend.Fragment
import org.jetbrains.amper.frontend.TaskName
import org.jetbrains.amper.incrementalcache.IncrementalCache
import org.jetbrains.amper.tasks.TaskOutputRoot
import org.jetbrains.amper.util.BuildType
import java.nio.file.Path
import kotlin.io.path.div

abstract class LastPhaseAndroidDelegatedGradleTask(
    private val module: AmperModule,
    buildType: BuildType,
    incrementalCache: IncrementalCache,
    androidSdkPath: Path,
    fragments: List<Fragment>,
    projectRoot: AmperProjectRoot,
    taskOutputPath: TaskOutputRoot,
    buildLogsRoot: AmperBuildLogsRoot,
    userCacheRoot: AmperUserCacheRoot,
    override val taskName: TaskName,
) : AndroidDelegatedGradleTask(
    module,
    buildType,
    incrementalCache,
    androidSdkPath,
    fragments,
    projectRoot,
    taskOutputPath,
    buildLogsRoot,
    userCacheRoot,
    taskName,
) {

    override val additionalInputFiles: List<Path> = if (buildType == BuildType.Release) {
        fragments
            .flatMap {
                buildList {
                    module.source.moduleDir.let { moduleDir ->
                        add((moduleDir / it.settings.android.signing.propertiesFile).toAbsolutePath())
                        add(moduleDir / "proguard-rules.pro")
                    }
                }
            }
    } else listOf()
}
