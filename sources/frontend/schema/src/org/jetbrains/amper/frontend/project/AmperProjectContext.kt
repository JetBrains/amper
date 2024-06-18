/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
     *
     * This may have a different definition depending on the type of Amper project:
     * * in standalone Amper, this directory contains either a project file or a module file (or both)
     * * in Gradle-based Amper, this is the directory of the root project (containing `settings.gradle.kts`)
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

    /**
     * The paths to the gradle build files of subprojects that belong to the Gradle build running Amper, but that don't
     * use Amper themselves.
     *
     * This is only meaningful in Gradle-based Amper projects. The goal is to be able to reference "pure Gradle"
     * subprojects as dependencies from Amper modules.
     */
    val gradleBuildFilesWithoutAmper: List<VirtualFile>
}
