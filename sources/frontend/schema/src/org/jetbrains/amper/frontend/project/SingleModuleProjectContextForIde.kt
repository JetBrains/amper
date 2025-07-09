/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.project

import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.amper.frontend.FrontendPathResolver
import org.jetbrains.amper.frontend.catalogs.GradleVersionsCatalogFinder
import org.jetbrains.amper.frontend.catalogs.VersionsCatalogProvider
import org.jetbrains.amper.frontend.schema.InternalDependency
import java.nio.file.Path

/**
 * This is a hack to analyze a single module from a wider project, to get diagnostics in the IDE editor.
 */
internal class SingleModuleProjectContextForIde(
    moduleFile: VirtualFile,
    override val frontendPathResolver: FrontendPathResolver,
) : AmperProjectContext, VersionsCatalogProvider by GradleVersionsCatalogFinder(FrontendPathResolver()) {

    init {
        require(moduleFile.name in amperModuleFileNames) {
            "This context type can only be created for a module file, got $moduleFile"
        }
    }

    override val projectRootDir: VirtualFile
        get() = error("The project root is undefined for a single-module context")

    override val amperModuleFiles: List<VirtualFile> = listOf(moduleFile)

    override val projectBuildDir: Path
        get() = error("The project build directory is undefined for a single-module context")

    override val pluginDependencies: List<InternalDependency>
        get() = emptyList()

    override val amperCustomTaskFiles: List<VirtualFile> = emptyList()
}
