/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.project

import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.amper.frontend.FrontendPathResolver
import org.jetbrains.amper.frontend.VersionCatalog
import org.jetbrains.amper.frontend.schema.InternalDependency
import java.nio.file.Path

/**
 * Contains information about the project as a whole.
 */
interface AmperProjectContext {

    /**
     * The [FrontendPathResolver] to do the mapping between paths, virtual files, and PSI files.
     */
    val frontendPathResolver: FrontendPathResolver

    /**
     * The root directory of this Amper project.
     * This directory contains either a project file, or a module file, or both.
     */
    val projectRootDir: VirtualFile

    /**
     * This is the build directory of the project.
     */
    val projectBuildDir: Path

    /**
     * The version catalog of the project, usually located at `gradle/libs.versions.toml` under the root.
     */
    val projectVersionsCatalog: VersionCatalog?

    /**
     * The paths to all Amper module files that belong to this project.
     */
    val amperModuleFiles: List<VirtualFile>

    /**
     * The paths to all custom tasks files that belong to this project.
     */
    val amperCustomTaskFiles: List<VirtualFile>

    /**
     * Plugin dependencies of this project.
     * NOTE: Currently, only local plugins are supported.
     */
    val pluginDependencies: List<InternalDependency>
}
