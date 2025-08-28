/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.project

import org.jetbrains.amper.frontend.TaskName
import java.nio.file.Path
import kotlin.io.path.div

val AmperProjectContext.pluginInternalDataFile: Path
    get() = projectBuildDir / ".plugins.json"

fun AmperProjectContext.getTaskOutputRoot(taskName: TaskName): Path {
    return projectBuildDir / "tasks" / taskName.name.replace(":", "_")
}