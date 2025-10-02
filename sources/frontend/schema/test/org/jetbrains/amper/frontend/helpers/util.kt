/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.helpers

import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.amper.core.system.SystemInfo
import org.jetbrains.amper.frontend.FrontendPathResolver
import org.jetbrains.amper.frontend.VersionCatalog
import org.jetbrains.amper.frontend.project.AmperProjectContext
import java.nio.file.Path

class TestSystemInfo(
    private val predefined: SystemInfo.Os
) : SystemInfo {
    override fun detect() = predefined
}

data class TestProjectContext(
    override val projectRootDir: VirtualFile,
    override val amperModuleFiles: List<VirtualFile>,
    override val frontendPathResolver: FrontendPathResolver,
) : AmperProjectContext {
    override val amperCustomTaskFiles: List<VirtualFile> = emptyList()
    override val pluginModuleFiles: List<VirtualFile> = emptyList()
    override val projectBuildDir: Path get() = projectRootDir.toNioPath()
    override var projectVersionsCatalog: VersionCatalog? = null
}
