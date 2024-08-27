/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.backend.test

import org.jetbrains.amper.cli.AmperBackend
import org.jetbrains.amper.cli.CliContext
import org.jetbrains.amper.frontend.TaskName
import java.nio.file.Path
import kotlin.io.path.div

suspend fun AmperBackend.runTask(vararg parts: String) = runTask(TaskName.fromHierarchy(parts.toList()))

internal fun CliContext.getTaskOutputPath(taskName: TaskName): Path =
    buildOutputRoot.path / "tasks" / taskName.name.replace(':', '_')
