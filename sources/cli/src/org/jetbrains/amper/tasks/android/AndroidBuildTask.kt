/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.tasks.android

import org.jetbrains.amper.android.AndroidBuildRequest
import org.jetbrains.amper.cli.AmperBuildLogsRoot
import org.jetbrains.amper.cli.AmperProjectRoot
import org.jetbrains.amper.frontend.Fragment
import org.jetbrains.amper.frontend.PotatoModule
import org.jetbrains.amper.frontend.TaskName
import org.jetbrains.amper.tasks.TaskOutputRoot
import org.jetbrains.amper.util.BuildType
import org.jetbrains.amper.util.ExecuteOnChangedInputs
import java.nio.file.Path

class AndroidBuildTask(
    module: PotatoModule,
    buildType: BuildType,
    executeOnChangedInputs: ExecuteOnChangedInputs,
    androidSdkPath: Path,
    fragments: List<Fragment>,
    projectRoot: AmperProjectRoot,
    taskOutputPath: TaskOutputRoot,
    buildLogsRoot: AmperBuildLogsRoot,
    override val taskName: TaskName,
) : AndroidDelegatedGradleTask(
    module,
    buildType,
    executeOnChangedInputs,
    androidSdkPath,
    fragments,
    projectRoot,
    taskOutputPath,
    buildLogsRoot,
    taskName
) {
    override val phase: AndroidBuildRequest.Phase
        get() = AndroidBuildRequest.Phase.Build
}
