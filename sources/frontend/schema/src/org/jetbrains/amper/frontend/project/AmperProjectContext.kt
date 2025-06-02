/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.project

import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.amper.frontend.catalogs.VersionsCatalogProvider

/**
 * Contains information about the project as a whole.
 */
interface AmperProjectContext : VersionsCatalogProvider {
    /**
     * The root directory of this Amper project.
     * This directory contains either a project file, or a module file, or both.
     */
    val projectRootDir: VirtualFile

    /**
     * The paths to all Amper module files that belong to this project.
     */
    val amperModuleFiles: List<VirtualFile>

    /**
     * The paths to all custom tasks files that belong to this project.
     */
    val amperCustomTaskFiles: List<VirtualFile>
}
