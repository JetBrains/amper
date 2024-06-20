/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.project

import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.amper.frontend.FrontendPathResolver
import org.jetbrains.amper.frontend.catalogs.GradleVersionsCatalogFinder
import java.nio.file.Path

private val gradleModuleFileNames = setOf("build.gradle.kts", "build.gradle")

/**
 * An [AmperProjectContext] for Gradle-based Amper.
 */
internal class GradleAmperProjectContext(
    override val frontendPathResolver: FrontendPathResolver,
    /**
     * The Gradle root project directory, which is also the root for Amper.
     */
    override val projectRootDir: VirtualFile,
    /**
     * The paths to the directories of all subprojects, including Amper-based and pure Gradle.
     */
    subprojectDirs: List<VirtualFile>,
) : AmperProjectContext {

    override val amperModuleFiles: List<VirtualFile> =
        subprojectDirs.mapNotNull { it.findChildMatchingAnyOf(amperModuleFileNames) }

    override val amperCustomTaskFiles: List<VirtualFile> = emptyList()

    override val gradleBuildFilesWithoutAmper: List<VirtualFile> =
        subprojectDirs.filterNot { it in amperModuleFiles.map { amp -> amp.parent } }
            .mapNotNull { it.findChildMatchingAnyOf(gradleModuleFileNames) }

    override fun getCatalogPathFor(file: VirtualFile): VirtualFile? =
        GradleVersionsCatalogFinder.findDefaultCatalogIn(projectRootDir)

    companion object {

        /**
         * Creates an [AmperProjectContext] for Gradle-based Amper.
         *
         * This uses a mock IntelliJ project for mapping files to virtual files and PSI files.
         *
         * @param rootProjectDir The project directory of the Gradle root project, which will serve as Amper root.
         * @param subprojectDirs The paths to the directories of all subprojects, including Amper-based and pure Gradle.
         */
        fun fromNio(rootProjectDir: Path, subprojectDirs: List<Path>): GradleAmperProjectContext {
            val frontendPathResolver = FrontendPathResolver(project = null)
            return GradleAmperProjectContext(
                frontendPathResolver = frontendPathResolver,
                projectRootDir = frontendPathResolver.loadVirtualFile(rootProjectDir),
                subprojectDirs = subprojectDirs.map { frontendPathResolver.loadVirtualFile(it) },
            )
        }
    }
}
