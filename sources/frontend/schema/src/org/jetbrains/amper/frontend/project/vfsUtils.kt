/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.project

import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileVisitor

internal val amperModuleFileNames = setOf("module.yaml", "module.amper")
internal val amperTaskFileSuffixes = setOf(".task.yaml", ".task.amper")

internal fun VirtualFile.isAmperModuleFile() = name in amperModuleFileNames
internal fun VirtualFile.isAmperCustomTaskFile() = amperTaskFileSuffixes.any { name.endsWith(it) }

/**
 * Extract the custom task name from this custom task file name.
 */
internal fun VirtualFile.customTaskName(): String {
    val taskName = amperTaskFileSuffixes.fold(name) { name, suffix -> name.removeSuffix(suffix) }
    if (name == taskName) {
        error("File '$name' is not a custom task file")
    }
    return taskName
}

internal fun VirtualFile.hasChildMatchingAnyOf(names: Set<String>): Boolean = findChildMatchingAnyOf(names) != null

internal fun VirtualFile.findChildMatchingAnyOf(names: Set<String>): VirtualFile? = children.find { it.name in names }

/**
 * Walks the tree of descendants, applying [action] to each of them.
 * If [action] returns `false` on a directory, the descendants of that directory are skipped.
 */
internal fun VirtualFile.forEachDescendant(action: (VirtualFile) -> Boolean) {
    VfsUtilCore.visitChildrenRecursively(this, object : VirtualFileVisitor<VirtualFile>() {
        override fun visitFile(file: VirtualFile): Boolean = action(file)
    })
}

/**
 * Returns the list of descendants of this [VirtualFile] matching the given [predicate].
 */
internal fun VirtualFile.filterDescendants(predicate: (VirtualFile) -> Boolean): List<VirtualFile> = buildList {
    forEachDescendant { file ->
        if (predicate(file)) add(file)
        true
    }
}
