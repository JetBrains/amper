/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.tasks.android

import org.jetbrains.amper.tasks.TaskResult
import java.nio.file.Path

/**
 * Interface for task results that contribute Android Assets to be packaged into the application.
 */
interface AdditionalAndroidAssetsProvider : TaskResult {
    val assetsRoots: List<AssetsRoot>

    /**
     * Example:
     * Given `AssetsRoot(path = "C:\\root\\assets\\", relativePackagingPath = "hello/world")`, the contents of the
     * `C:\root\assets` will be packaged as the android assets under the `hello/world` path.
     */
    data class AssetsRoot(
        /**
         * Root directory containing assets.
         */
        val path: Path,

        /**
         * The path with the uniform path separator '/' relative to the package root.
         */
        val relativePackagingPath: String,
    )
}
