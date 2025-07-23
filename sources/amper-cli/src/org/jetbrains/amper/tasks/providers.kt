/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.tasks

import java.nio.file.Path

/**
 * Provides additional JVM classes that should be considered as compile dependencies.
 */
internal interface AdditionalClasspathProvider {
    /**
     * Paths to classes, jars, or directories containing compile dependencies.
     */
    val compileClasspath: List<Path>
}
