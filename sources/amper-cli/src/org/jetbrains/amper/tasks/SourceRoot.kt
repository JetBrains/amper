/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.tasks

import java.nio.file.Path

/**
 * A Kotlin/Java source root (directory associated with a fragment) for compilation.
 */
data class SourceRoot(
    /** The name of the existing fragment that the sources should be associated to. */
    val fragmentName: String,
    /** The absolute path to the directory containing the sources. */
    val path: Path,
) {
    init {
        require(path.isAbsolute) { "Source root path should be absolute, got '$path'" }
    }
}