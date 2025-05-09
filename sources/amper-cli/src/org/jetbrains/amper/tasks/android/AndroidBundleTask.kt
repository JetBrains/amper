/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.tasks.android

import org.jetbrains.amper.android.AndroidBuildRequest
import org.jetbrains.amper.cli.AmperBuildLogsRoot
import org.jetbrains.amper.cli.AmperProjectRoot
import org.jetbrains.amper.engine.PackageTask
import org.jetbrains.amper.frontend.AmperModule
import org.jetbrains.amper.frontend.Fragment
import org.jetbrains.amper.frontend.Platform
import org.jetbrains.amper.frontend.TaskName
import org.jetbrains.amper.incrementalcache.ExecuteOnChangedInputs
import org.jetbrains.amper.tasks.TaskOutputRoot
import org.jetbrains.amper.util.BuildType
import java.nio.file.Path

class AndroidBundleTask(
    override val module: AmperModule,
    override val buildType: BuildType,
    executeOnChangedInputs: ExecuteOnChangedInputs,
    androidSdkPath: Path,
    fragments: List<Fragment>,
    projectRoot: AmperProjectRoot,
    taskOutputPath: TaskOutputRoot,
    buildLogsRoot: AmperBuildLogsRoot,
    override val taskName: TaskName,
) : LastPhaseAndroidDelegatedGradleTask(
    module,
    buildType,
    executeOnChangedInputs,
    androidSdkPath,
    fragments,
    projectRoot,
    taskOutputPath,
    buildLogsRoot,
    taskName
), PackageTask {
    override val phase: AndroidBuildRequest.Phase
        get() = AndroidBuildRequest.Phase.Bundle

    override val platform: Platform
        get() = Platform.ANDROID

    override val format: PackageTask.Format
        get() = PackageTask.Format.Aab

}
