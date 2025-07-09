/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.schema.helper

import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.amper.core.system.SystemInfo
import org.jetbrains.amper.frontend.FrontendPathResolver
import org.jetbrains.amper.frontend.api.TraceableString
import org.jetbrains.amper.frontend.project.AmperProjectContext
import org.jetbrains.amper.frontend.schema.InternalDependency
import org.jetbrains.amper.test.golden.GoldenTest
import java.nio.file.Path
import kotlin.io.path.copyTo
import kotlin.io.path.createDirectories
import kotlin.io.path.div
import kotlin.io.path.exists

fun GoldenTest.copyLocal(localName: String, dest: Path = buildDir(), newPath: () -> Path = { dest / localName }) {
    val localFile = baseTestResourcesPath().resolve(localName).normalize().takeIf(Path::exists)
    val newPathWithDirs = newPath().apply { createDirectories() }
    localFile?.copyTo(newPathWithDirs, overwrite = true)
}

class TestSystemInfo(
    private val predefined: SystemInfo.Os
) : SystemInfo {
    override fun detect() = predefined
}

open class TestProjectContext(
    override val projectRootDir: VirtualFile,
    override val amperModuleFiles: List<VirtualFile>,
    override val frontendPathResolver: FrontendPathResolver,
) : AmperProjectContext {
    override val amperCustomTaskFiles: List<VirtualFile> = emptyList()
    override val pluginDependencies: List<InternalDependency> = emptyList()
    override val projectBuildDir: Path get() = projectRootDir.toNioPath()
    val path2catalog: MutableMap<VirtualFile, VirtualFile> = mutableMapOf()
    override fun getCatalogPathFor(file: VirtualFile): VirtualFile? = path2catalog[file]
}

fun listOfTraceable(vararg items: String) = items.map { TraceableString(it) }

