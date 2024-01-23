/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.tasks

import org.jetbrains.amper.engine.Task
import org.jetbrains.amper.frontend.Platform
import org.jetbrains.amper.frontend.PotatoModule

interface RunTask : Task {
    val platform: Platform
    val module: PotatoModule
}
