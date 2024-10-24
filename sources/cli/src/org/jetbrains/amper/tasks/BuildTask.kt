/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.tasks

import org.jetbrains.amper.engine.Task
import org.jetbrains.amper.frontend.Platform
import org.jetbrains.amper.frontend.AmperModule

/**
 * A task attached to the 'build' command.
 */
interface BuildTask : Task {
    val module: AmperModule
    val isTest: Boolean
    val platform: Platform
}
