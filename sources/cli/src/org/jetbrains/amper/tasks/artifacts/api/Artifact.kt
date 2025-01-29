/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.tasks.artifacts.api

import java.nio.file.Path

/**
 * A typed FS location.
 */
interface Artifact {
    /**
     * A file system location that may contain:
     * 1. A single regular file
     * 2. A directory, in which case the artifact owns the whole subtree.
     * 3. Nothing (does not exist)
     *
     * The path is unique in the scope of the project, and one path cannot be a child of another.
     */
    val path: Path
}
