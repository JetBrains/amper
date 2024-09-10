/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.ksp

import kotlinx.serialization.Serializable
import java.nio.file.Path

@Serializable
class KspOutputPaths(
    /**
     * The base dir of the module.
     * KSP uses it to relativize paths for incrementality processing and output tracking.
     */
    val moduleBaseDir: Path,
    /**
     * The directory to place all KSP caches.
     */
    val cachesDir: Path,
    /**
     * The output directory for generated Kotlin sources.
     */
    val kotlinSourcesDir: Path,
    /**
     * The output directory for generated Java sources.
     */
    val javaSourcesDir: Path,
    /**
     * The output directory for generated resources.
     */
    val resourcesDir: Path,
    /**
     * The output directory for generated classes.
     */
    val classesDir: Path,
) {
    /**
     * A directory that contains all output dirs.
     *
     * KSP uses it to relativize paths to replicate the output dirs hierarchy in a backup directory, so it's ok if it's
     * not a direct parent of each dir, so long as all paths are in there.
     */
    val outputsBaseDir: Path = closestAncestorOf(kotlinSourcesDir, javaSourcesDir, resourcesDir, classesDir)
}

/**
 * Returns the path to the closest ancestor of all the given [paths].
 */
private fun closestAncestorOf(vararg paths: Path): Path = paths.reduce { p1, p2 ->
    closestAncestorOf(p1.normalize(), p2.normalize()) ?: error("No common ancestor for KSP output dirs $p1 and $p2")
}

/**
 * Returns the path to the closest ancestor of [path1] and [path2].
 */
private fun closestAncestorOf(path1: Path, path2: Path): Path? {
    // We can't know anything if the paths don't have roots
    val root1 = path1.root ?: return null
    val root2 = path2.root ?: return null

    // If roots are different, there can't be a common ancestor
    if (root1 != root2) {
        return null
    }

    val minLength = minOf(path1.nameCount, path2.nameCount)
    val firstDifferenceIndex = (0..<minLength).find { path1.getName(it) != path2.getName(it) }

    return if (firstDifferenceIndex != null) {
        root1.resolve(path1.subpath(0, firstDifferenceIndex))
    } else {
        path1
    }
}
