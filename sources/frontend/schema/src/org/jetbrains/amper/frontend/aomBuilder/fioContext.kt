/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.aomBuilder

import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileVisitor
import com.intellij.openapi.vfs.readText
import org.jetbrains.amper.frontend.catalogs.GradleVersionsCatalogFinder
import java.nio.file.Path
import kotlin.io.path.div

private const val amperModuleFileName = "module.yaml"
private const val moduleAmperFileName = "module.amper"
private const val amperIgnoreFileName = ".amperignore"

const val amperCustomTaskSuffix = ".task.amper"
const val yamlCustomTaskSuffix = ".task.yaml"

private fun VirtualFile.isModuleYaml() = name == amperModuleFileName || name == moduleAmperFileName
private fun VirtualFile.isCustomTask() = name.endsWith(amperCustomTaskSuffix) || name.endsWith(yamlCustomTaskSuffix)

/**
 * Extract the custom task name from this custom task file name.
 */
internal fun VirtualFile.customTaskName(): String {
    val taskName = name.removeSuffix(amperCustomTaskSuffix).removeSuffix(yamlCustomTaskSuffix)
    if (name == taskName) {
        error("File '$name' is not a custom task file")
    }
    return taskName
}

private val gradleModuleFiles = setOf("build.gradle.kts", "build.gradle")

private fun FioContext.isIgnored(file: VirtualFile): Boolean {
    val nioPath = file.fileSystem.getNioPath(file)?.toFile() ?: return false
    return ignorePaths.any { VfsUtilCore.isAncestor(it.toFile(), nioPath, false) }
}

/**
 * Files context used to parse amper modules.
 */
internal interface FioContext : VersionsCatalogFinder {

    /**
     * `.amperignore` file parsed lines as paths.
     */
    // TODO remove once project scope is implemented
    val ignorePaths: List<Path>

    /**
     * All amper module files ([amperModuleFileName]), that are found within [rootDir] hierarchy.
     */
    val amperModuleFiles: List<VirtualFile>

    /**
     * All custom tasks files, that are found within [rootDir] hierarchy.
     */
    val amperCustomTaskFiles: List<VirtualFile>

    /**
     * All gradle modules ([gradleModuleFiles]), that are found within [rootDir] hierarchy.
     */
    val gradleModules: Map<VirtualFile, DumbGradleModule>
}

internal interface VersionsCatalogFinder {

    /**
     * Try to find catalog path for given module or template path.
     */
    fun getCatalogPathFor(file: VirtualFile): VirtualFile?
}

/**
 * Default files context.
 */
internal open class DefaultFioContext(root: VirtualFile) : FioContext {
    private val rootDir: VirtualFile = root.takeIf { it.isDirectory }
        ?: root.parent
        ?: error("Should not call with a root without parent")

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

    override val amperModuleFiles: List<VirtualFile>
        get() = filesFromRoot.moduleFiles

    override val amperCustomTaskFiles: List<VirtualFile>
        get() = filesFromRoot.customTaskFiles

    override val gradleModules: Map<VirtualFile, DumbGradleModule> by lazy {
        buildMap {
            // TODO This seems wrong. We should be using the included projects from settings.gradle.kts, otherwise
            //   we may pick up Gradle projects that are unrelated to our build
            VfsUtilCore.visitChildrenRecursively(rootDir, object : VirtualFileVisitor<VirtualFile>() {
                override fun visitFile(file: VirtualFile): Boolean {
                    if (isIgnored(file)) return false
                    if (file.parent in amperModuleFiles.map { it.parent }) return true
                    if (file.name in gradleModuleFiles) put(file.parent, DumbGradleModule(file))
                    return true
                }
            })
        }
    }

    private class AmperFiles(val moduleFiles: List<VirtualFile>, val customTaskFiles: List<VirtualFile>)

    private val filesFromRoot: AmperFiles by lazy {
        val moduleFiles = mutableListOf<VirtualFile>()
        val customTaskFiles = mutableListOf<VirtualFile>()

        VfsUtilCore.visitChildrenRecursively(rootDir, object : VirtualFileVisitor<VirtualFile>() {
            override fun visitFile(file: VirtualFile): Boolean {
                if (isIgnored(file)) return false
                if (file.isModuleYaml()) moduleFiles.add(file)
                if (file.isCustomTask()) customTaskFiles.add(file)
                return true
            }
        })

        AmperFiles(moduleFiles = moduleFiles, customTaskFiles = customTaskFiles)
    }

    private val catalogFinder = GradleVersionsCatalogFinder(rootDir)

    override fun getCatalogPathFor(file: VirtualFile): VirtualFile? = catalogFinder.getCatalogPathFor(file)
}

/**
 * Per-file context.
 */
internal class SingleModuleFioContext(
    moduleFile: VirtualFile,
    projectRootDir: VirtualFile,
) : DefaultFioContext(projectRootDir) {

    init {
        require(moduleFile.isModuleYaml()) {
            "This context type can only be created for a module file, got $moduleFile"
        }
    }

    override val amperCustomTaskFiles: List<VirtualFile> = emptyList()

    override val amperModuleFiles: List<VirtualFile> = if (isIgnored(moduleFile)) emptyList() else listOf(moduleFile)

    override val gradleModules: Map<VirtualFile, DumbGradleModule> = emptyMap()
}
