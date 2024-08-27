/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.ksp

import kotlinx.serialization.Serializable
import java.nio.file.Path
import kotlin.io.path.div

@Serializable
class KspOutputPaths(
    /**
     * The directory to place all KSP caches.
     */
    val cachesDir: Path,
    /**
     * The common parent of all output dirs.
     * KSP uses it to relativize paths to replicate the output dirs hierarchy in a backup directory.
     */
    val outputsBaseDir: Path,
    /**
     * The base dir of the module.
     * KSP uses it to relativize paths for incrementality processing and output tracking.
     */
    val moduleBaseDir: Path,
) {
    val kotlinSourcesDir = outputsBaseDir / "kotlin"
    val javaSourcesDir = outputsBaseDir / "java"
    val classesDir = outputsBaseDir / "classes"
    val resourcesDir = outputsBaseDir / "resources"
}
