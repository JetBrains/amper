/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.tasks.jvm

import org.jetbrains.amper.tasks.TaskResult
import java.nio.file.Path

/**
 * Provides a path to be included in the runtime classpath.
 *
 * NOTE: May contain platform-specific entries for certain platforms.
 *
 * @see JvmRuntimeClasspathTask
 */
interface RuntimeClasspathElementProvider : TaskResult {
    val paths: List<Path>
}
