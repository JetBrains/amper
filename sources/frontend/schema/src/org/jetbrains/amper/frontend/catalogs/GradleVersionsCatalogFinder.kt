/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.catalogs

import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.amper.frontend.FrontendPathResolver

private const val gradleDefaultVersionCatalogName = "libs.versions.toml"
private const val gradleDirName = "gradle"

internal class GradleVersionsCatalogFinder(
    override val frontendPathResolver: FrontendPathResolver
) : VersionsCatalogProvider {

    // This wrapper allows caching the absence of catalog too
    private data class CatalogPathResult(val path: VirtualFile?)

    private val knownGradleCatalogs = mutableMapOf<VirtualFile, CatalogPathResult>()

    override fun getCatalogPathFor(file: VirtualFile): VirtualFile? = knownGradleCatalogs
        .computeIfAbsent(file) { CatalogPathResult(it.findGradleCatalog()) }
        .path

    /**
     * Go up the file tree from [this] dir, and search for gradle/libs.versions.toml until we reach the fs root.
     *
     * This is a poor man's heuristic to find the versions catalog when we don't have a real project context, but we
     * can't search in the actual project root directly because we don't know what the root is.
     *
     * We can't use Project.guessProjectDir either as project root because it breaks in several cases:
     * * the IJ root might be higher in the file system hierarchy than the actual Amper project root
     * * the IJ root might be completely different if the Amper project is in an included Gradle build
     * * the IJ root might be a subdir of the actual project root for a short time after project opening
     */
    private fun VirtualFile.findGradleCatalog(): VirtualFile? {
        val dirsUpToRoot = generateSequence(this) { it.parent }.filter { it.isDirectory }

        return dirsUpToRoot
            .mapNotNull { findDefaultCatalogIn(it) }
            .firstOrNull()
    }

    companion object {
        internal fun findDefaultCatalogIn(dir: VirtualFile) =
            dir.findChild(gradleDirName)?.findChild(gradleDefaultVersionCatalogName)
    }
}
