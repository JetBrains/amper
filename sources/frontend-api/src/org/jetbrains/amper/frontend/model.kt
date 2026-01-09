/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend

import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.amper.core.UsedInIdePlugin
import java.nio.file.Path
import kotlin.io.path.absolute

/**
 * The Amper project model.
 */
interface Model {
    /**
     * The path to the root directory of this project.
     *
     * This is the directory containing the `project.yaml` (or `module.yaml` file for single-module projects).
     */
    val projectRoot: Path

    /**
     * The modules declared in this project.
     */
    val modules: List<AmperModule>

    /**
     * The module files that are too broken to even create an [AmperModule] instance.
     *
     * If not empty, the corresponding errors have been reported via the
     * [ProblemReporter][org.jetbrains.amper.problems.reporting.ProblemReporter] passed when creating this [Model].
     */
    val unreadableModuleFiles: Set<VirtualFile>
}

/**
 * Gets the [AmperModule] that was read from the given [moduleFile] in this [Model], or null if there is no such
 * module.
 */
@UsedInIdePlugin
fun Model.getModule(moduleFile: VirtualFile): AmperModule? {
    val moduleFilePath = moduleFile.parent?.toNioPath()?.normalize()?.absolute()
    return modules.find { it.source.moduleDir.normalize().absolute() == moduleFilePath }
}
