/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.aomBuilder

import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileVisitor
import com.intellij.openapi.vfs.readText
import java.nio.file.Path
import kotlin.io.path.div

private const val amperModuleFileName = "module.yaml"
private const val amperIgnoreFileName = ".amperignore"
private fun VirtualFile.isModuleYaml() = name == amperModuleFileName

private val gradleModuleFiles = setOf("build.gradle.kts", "build.gradle")
private const val gradleDefaultVersionCatalogName = "libs.versions.toml"
private const val gradleDirName = "gradle"

private fun FioContext.isIgnored(file: VirtualFile): Boolean {
    val nioPath = file.fileSystem.getNioPath(file)?.toFile() ?: return false
    return ignorePaths.any { VfsUtilCore.isAncestor(it.toFile(), nioPath, false) }
}

/**
 * Files context used to parse amper modules.
 */
interface FioContext {
    val root: VirtualFile

    val rootDir: VirtualFile

    /**
     * `.amperignore` file parsed lines as paths, relative to [rootDir].
     */
    val ignorePaths: List<Path>

    /**
     * All amper module files ([amperModuleFileName]), that are found within [rootDir] hierarchy.
     */
    val amperModuleFiles: List<VirtualFile>

    /**
     * Parents of [amperModuleFiles].
     */
    val amperModuleDirs: List<VirtualFile>

    /**
     * All gradle modules ([gradleModuleFiles]), that are found within [rootDir] hierarchy.
     */
    val gradleModules: Map<VirtualFile, DumbGradleModule>

    /**
     * Try to find catalog path for given module or template path.
     */
    fun getCatalogPathFor(file: VirtualFile): VirtualFile?
}

/**
 * Default files context.
 */
open class DefaultFioContext(
    override val root: VirtualFile
) : FioContext {
    override val rootDir: VirtualFile by lazy {
        root.takeIf { it.isDirectory }
            ?: root.parent
            ?: error("Should not call with a root without parent")
    }

    override val ignorePaths: List<Path> by lazy {
        rootDir.findChild(amperIgnoreFileName)
            ?.readText()
            ?.lines()
            .orEmpty()
            .map { it.trim() }
            // Ignore comments.
            .filter { !it.startsWith("#") }
            .filter { it.isNotBlank() }
            .map { rootDir.toNioPath() / it }
    }

    override val amperModuleFiles: List<VirtualFile> by lazy {
        buildList {
            VfsUtil.visitChildrenRecursively(rootDir, object : VirtualFileVisitor<VirtualFile>() {
                override fun visitFile(file: VirtualFile): Boolean {
                    if (isIgnored(file)) return false
                    if (file.isModuleYaml()) add(file)
                    return true
                }
            })
        }
    }

    override val amperModuleDirs: List<VirtualFile> by lazy { amperModuleFiles.map { it.parent } }

    override val gradleModules: Map<VirtualFile, DumbGradleModule> by lazy {
        buildMap {
            VfsUtil.visitChildrenRecursively(rootDir, object : VirtualFileVisitor<VirtualFile>() {
                override fun visitFile(file: VirtualFile): Boolean {
                    if (isIgnored(file)) return false
                    if (file.parent in amperModuleDirs) return true
                    if (file.name in gradleModuleFiles) put(file.parent, DumbGradleModule(file))
                    return true
                }
            })
        }
    }

    data class CatalogPathHolder(val path: VirtualFile?)

    private val knownGradleCatalogs = mutableMapOf<VirtualFile, CatalogPathHolder>()

    override fun getCatalogPathFor(file: VirtualFile): VirtualFile? = knownGradleCatalogs
        .computeIfAbsent(file) { CatalogPathHolder(it.findGradleCatalog()) }
        .path

    /**
     * Find "libs.versions.toml" in every gradle directory between [this] path and [rootDir]
     * with deeper files being the first.
     */
    private fun VirtualFile.findGradleCatalog(): VirtualFile? {
        assert(VfsUtil.isAncestor(rootDir, this, true)) {
            "Cannot call with the file $this that is outside of $rootDir)"
        }
        val currentDir = takeIf { it.isDirectory } ?: parent

        // Directories from [this] to [rootDir], both ends including.
        val directories = if (currentDir == rootDir) {
            listOf(currentDir)
        } else {
            generateSequence(currentDir) { dir ->
                dir.parent.takeIf { it != rootDir }
            }.filter { it.isDirectory }.toList() + listOf(rootDir)
        }

        return directories.asSequence()
            .mapNotNull { it.findChild(gradleDirName)?.findChild(gradleDefaultVersionCatalogName) }
            .filter { !isIgnored(it) }
            .firstOrNull()
    }
}

/**
 * Per-file context.
 */
class ModuleFioContext(
    virtualFile: VirtualFile,
    project: Project,
) : DefaultFioContext(requireNotNull(project.guessProjectDir()) { "Project doesn't have base directory" }) {

    private val requiredDir = if (virtualFile.isDirectory) virtualFile else virtualFile.parent

    override val amperModuleFiles: List<VirtualFile> by lazy {
        requiredDir.children
            .filter { file -> file.isModuleYaml() && !isIgnored(file) }
    }

    override val gradleModules: Map<VirtualFile, DumbGradleModule> by lazy {
        emptyMap()
    }
}
