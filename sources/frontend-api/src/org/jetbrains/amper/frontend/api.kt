/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend

import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.amper.core.UsedInIdePlugin
import java.nio.file.Path
import kotlin.io.path.absolute

interface Model {
    val projectRoot: Path
    val modules: List<AmperModule>
}

/**
 * Gets the [AmperModule] that was read from the given [moduleFile] in this [Model], or null if there is no such
 * module.
 */
@UsedInIdePlugin
fun Model.getModule(moduleFile: VirtualFile): AmperModule? {
    val moduleFilePath = moduleFile.parent?.toNioPath()?.normalize()?.absolute()
    return modules.find { it.source.moduleDir?.normalize()?.absolute() == moduleFilePath }
}
