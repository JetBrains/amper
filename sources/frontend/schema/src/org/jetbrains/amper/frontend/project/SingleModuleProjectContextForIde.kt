/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.project

import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.amper.frontend.FrontendPathResolver
import org.jetbrains.amper.frontend.catalogs.GradleVersionsCatalogFinder
import org.jetbrains.amper.frontend.catalogs.VersionsCatalogProvider

/**
 * This is a hack to analyze a single module from a wider project, to get diagnostics in the IDE editor.
 */
internal class SingleModuleProjectContextForIde(
    moduleFile: VirtualFile,
    override val frontendPathResolver: FrontendPathResolver,
    /**
     * The root directory of the Amper project or the IntelliJ project.
     */
    override val projectRootDir: VirtualFile,
) : AmperProjectContext, VersionsCatalogProvider by GradleVersionsCatalogFinder(projectRootDir) {

    init {
        require(moduleFile.name in amperModuleFileNames) {
            "This context type can only be created for a module file, got $moduleFile"
        }
    }

    override val amperModuleFiles: List<VirtualFile> = listOf(moduleFile)

    override val amperCustomTaskFiles: List<VirtualFile> = emptyList()

    override val gradleBuildFilesWithoutAmper: List<VirtualFile> = emptyList()
}