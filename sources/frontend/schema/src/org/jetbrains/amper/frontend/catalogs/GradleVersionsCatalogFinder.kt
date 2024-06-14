/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.catalogs

import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.amper.frontend.aomBuilder.VersionsCatalogFinder

private const val gradleDefaultVersionCatalogName = "libs.versions.toml"
private const val gradleDirName = "gradle"

internal class GradleVersionsCatalogFinder(
    /**
     * The root directory of the Amper project or the IntelliJ project.
     */
    private val rootDir: VirtualFile,
) : VersionsCatalogFinder {

    // This wrapper allows to cache the absence of catalog too
    private data class CatalogPathResult(val path: VirtualFile?)

    private val knownGradleCatalogs = mutableMapOf<VirtualFile, CatalogPathResult>()

    override fun getCatalogPathFor(file: VirtualFile): VirtualFile? = knownGradleCatalogs
        .computeIfAbsent(file) { CatalogPathResult(it.findGradleCatalog()) }
        .path

    /**
     * Find "libs.versions.toml" in every gradle directory between [this] path and [rootDir]
     * with deeper files being the first.
     */
    private fun VirtualFile.findGradleCatalog(): VirtualFile? {
        assert(VfsUtilCore.isAncestor(rootDir, this, true)) {
            "Cannot lookup the catalog for file $this, because it is outside the root directory $rootDir)"
        }
        // We can't search in the root directly because the rootDir is sometimes the IntelliJ root,
        // and thus it might be higher in the file system hierarchy than the actual project root.
        val dirsUpToRoot = generateSequence(this) { it.parent }
            .filter { it.isDirectory }
            .takeWhile { it != rootDir } + rootDir

        return dirsUpToRoot
            .mapNotNull { it.findChild(gradleDirName)?.findChild(gradleDefaultVersionCatalogName) }
            .firstOrNull()
    }
}
