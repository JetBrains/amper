/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.catalogs

import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.amper.frontend.FrontendPathResolver
import org.jetbrains.amper.frontend.VersionCatalog

private const val gradleDefaultVersionCatalogName = "libs.versions.toml"
private const val gradleDirName = "gradle"

@RequiresOptIn("This uses a heuristic to find a Gradle version catalog up the tree. " +
        "We should instead use the real project context")
annotation class IncorrectCatalogDetection

internal object GradleVersionsCatalogFinder {

    /**
     * Go up the file tree from [this] dir, and search for gradle/libs.versions.toml until we reach the fs root.
     *
     * This is a poor man's heuristic to find the version catalog when we don't have a real project context, and we
     * can't search in the actual project root directly because we don't know what the root is.
     *
     * We can't use Project.guessProjectDir either as project root because it breaks in several cases:
     * * the IJ root might be higher in the file system hierarchy than the actual Amper project root
     * * the IJ root might be completely different if the Amper project is in an included Gradle build
     * * the IJ root might be a subdir of the actual project root for a short time after project opening
     */
    context(frontendPathResolver: FrontendPathResolver)
    @IncorrectCatalogDetection
    fun findGradleVersionCatalogUpTheTreeFrom(file: VirtualFile): VersionCatalog? {
        val dirsUpToRoot = generateSequence(file) { it.parent }.filter { it.isDirectory }
        return dirsUpToRoot.firstNotNullOfOrNull { getDefaultCatalogFrom(it) }
    }

    context(frontendPathResolver: FrontendPathResolver)
    fun getDefaultCatalogFrom(dir: VirtualFile): VersionCatalog? {
        val catalogFile = dir.findChild(gradleDirName)?.findChild(gradleDefaultVersionCatalogName) ?: return null
        return frontendPathResolver.parseGradleVersionCatalog(catalogFile)
    }
}
