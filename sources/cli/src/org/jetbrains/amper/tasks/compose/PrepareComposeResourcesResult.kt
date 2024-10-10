/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.tasks.compose

import org.jetbrains.amper.tasks.TaskResult
import java.nio.file.Path

sealed interface PrepareComposeResourcesResult : TaskResult {
    /**
     * Normal result. [outputDir] contains at least one file.
     */
    class Prepared(
        val outputDir: Path,
        val relativePackagingPath: String,
    ) : PrepareComposeResourcesResult

    /**
     * aka NO-SOURCE.
     */
    data object NoResources : PrepareComposeResourcesResult
}