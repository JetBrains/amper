/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.project

import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.amper.frontend.FrontendPathResolver
import org.jetbrains.amper.frontend.VersionCatalog
import org.jetbrains.amper.frontend.schema.UnscopedExternalMavenDependency
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
     * The build directory of the project, containing project-specific outputs and caches.
     * It is usually located at `<projectRoot>/build`.
     */
    val projectBuildDir: Path

    /**
     * The version catalog of the project, usually located at `<projectRoot>/libs.versions.toml` or
     * `<projectRoot>/gradle/libs.versions.toml`.
     *
     * Null if the project doesn't have a version catalog file.
     */
    val projectVersionsCatalog: VersionCatalog?

    /**
     * The paths to all Amper module files that belong to this project.
     */
    val amperModuleFiles: List<VirtualFile>

    /**
     * Local plugin module files of this project. Subset of [amperModuleFiles].
     */
    val externalMavenPluginDependencies: List<UnscopedExternalMavenDependency>

    /**
     * Local plugin module files of this project. Subset of [amperModuleFiles].
     */
    val pluginsModuleFiles: List<VirtualFile>
}
