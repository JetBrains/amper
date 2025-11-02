/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.aomBuilder.plugins.taskGraph

import java.nio.file.Path
import kotlin.io.path.pathString

/**
 * Given a collection of `Path`s, this groups them by their most common parent path that is present in the collection.
 *
 * So for input paths like
 * - `/foo`
 * - `/foo/bar`
 * - `/foo/baz`
 * - `/foo/bar/quu`
 * - `/duu`
 * - `/duu/2`
 * - `/goo`
 *
 * the return value would be:
 * - `/foo`
 *     - `/foo`
 *     - `/foo/bar`
 *     - `/foo/baz`
 *     - `/foo/bar/quu`
 * - `/duu`
 *     - `/duu`
 *     - `/duu/2`
 * - `/goo`
 *     - `/goo`
 *
 * WARNING: all the paths should be in their canonical form. Otherwise, the behavior is undefined.
 */
internal fun <T> Iterable<T>.groupByRoots(
    pathSelector: (T) -> Path,
): Map<Path, List<T>> {
    // TODO: optimize this
    val sortedAscending = map { pathSelector(it) }.sortedBy { it.nameCount }

    return buildMap<Path, MutableList<T>> {
        for (element in this@groupByRoots) {
            val path = pathSelector(element)
            val root = sortedAscending.find { path.startsWith(it) }!!
            getOrPut(root, ::mutableListOf).add(element)
        }
    }
}

context(context: TaskGraphBuildContext)
internal fun Path.mustBeProduced(): Boolean = startsWith(context.buildDir)

context(context: TaskGraphBuildContext)
internal fun Path.replaceKnownSuperpaths(): String {
    return pathString
        .replace(context.buildDir.pathString, "<project-build-dir>")
        .replace(context.projectRootDir.pathString, "<project-root-dir>")
}