/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.project

import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.readText
import org.jetbrains.amper.frontend.catalogs.GradleVersionsCatalogFinder
import java.nio.file.Path
import kotlin.io.path.div

private val gradleModuleFileNames = setOf("build.gradle.kts", "build.gradle")
private const val amperIgnoreFileName = ".amperignore"

@Deprecated("Auto-discovery is deprecated. Use the StandaloneAmperProjectContext or GradleAmperProjectContext instead.")
internal class LegacyAutoDiscoveringProjectContext(
    override val projectRootDir: VirtualFile,
) : AmperProjectContext {

    private val ignorePaths: List<Path> by lazy {
        projectRootDir.findChild(amperIgnoreFileName)
            ?.readText()
            ?.lines()
            .orEmpty()
            .map { it.trim() }
            // Ignore comments.
            .filter { !it.startsWith("#") }
            .filter { it.isNotBlank() }
            .map { projectRootDir.toNioPath() / it }
    }

    override val amperModuleFiles: List<VirtualFile>
        get() = filesFromRoot.moduleFiles

    override val amperCustomTaskFiles: List<VirtualFile>
        get() = filesFromRoot.customTaskFiles

    private class AmperFiles(val moduleFiles: List<VirtualFile>, val customTaskFiles: List<VirtualFile>)

    private val filesFromRoot: AmperFiles by lazy {
        val moduleFiles = mutableListOf<VirtualFile>()
        val customTaskFiles = mutableListOf<VirtualFile>()

        projectRootDir.forEachDescendant { file ->
            if (isIgnored(file)) return@forEachDescendant false
            if (file.isAmperModuleFile()) moduleFiles.add(file)
            if (file.isAmperCustomTaskFile()) customTaskFiles.add(file)
            return@forEachDescendant true
        }

        AmperFiles(moduleFiles = moduleFiles, customTaskFiles = customTaskFiles)
    }

    override val gradleBuildFilesWithoutAmper: List<VirtualFile> by lazy {
        buildList {
            projectRootDir.forEachDescendant { file ->
                if (isIgnored(file)) return@forEachDescendant false
                if (file.parent in amperModuleFiles.map { it.parent }) return@forEachDescendant true
                if (file.name in gradleModuleFileNames) add(file)
                return@forEachDescendant true
            }
        }
    }

    private fun isIgnored(file: VirtualFile): Boolean =
        ignorePaths.any { VfsUtilCore.isAncestor(it.toFile(), file.toNioPath().toFile(), false) }

    private val catalogFinder = GradleVersionsCatalogFinder(projectRootDir)

    override fun getCatalogPathFor(file: VirtualFile): VirtualFile? = catalogFinder.getCatalogPathFor(file)
}
