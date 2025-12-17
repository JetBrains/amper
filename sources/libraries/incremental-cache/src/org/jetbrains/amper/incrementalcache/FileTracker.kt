/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.incrementalcache

import java.nio.file.FileVisitResult
import java.nio.file.Path
import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.attribute.PosixFileAttributes
import kotlin.io.path.pathString
import kotlin.io.path.readAttributes
import kotlin.io.path.visitFileTree

internal fun readFileStates(paths: List<Path>, excludedFiles: Set<Path>, failOnMissing: Boolean): Map<String, String> {
    return FileTracker(excludedFiles, failOnMissing).readFileStates(paths)
}

private class FileTracker(
    private val excludedFiles: Set<Path>,
    private val failOnMissing: Boolean,
) {
    private val stateByFilePath = mutableMapOf<String, String>()

    init {
        check(excludedFiles.all { it.isAbsolute }) {
            "Excluded file paths must be absolute. Got:\n${excludedFiles.joinToString("\n")}"
        }
    }

    fun readFileStates(paths: List<Path>): Map<String, String> {
        paths.forEach { path ->
            check(path.isAbsolute) { "Path must be absolute: $path" }
            if (path in excludedFiles) {
                return@forEach
            }

            val attr = getAttributes(path)
            when {
                attr == null -> recordMissingFile(path)
                // TODO this walk could be multi-threaded, it's trivial to implement with coroutines
                attr.isDirectory -> recordDirState(path)
                else -> recordFileState(path, attr)
            }
        }

        return stateByFilePath
    }

    private fun recordMissingFile(path: Path) {
        if (failOnMissing) {
            throw NoSuchFileException(file = path.toFile(), reason = "path from outputs is not found")
        } else {
            stateByFilePath[path.pathString] = "MISSING"
        }
    }

    private fun recordDirState(dir: Path) {
        var childrenCount = 0

        // Using Path.visitFileTree to get both file name AND file attributes at the same time.
        // This is much faster on OSes where you can get both, e.g., Windows.
        dir.visitFileTree {
            onPreVisitDirectory { subdir, _ ->
                if (dir == subdir) {
                    return@onPreVisitDirectory FileVisitResult.CONTINUE
                }
                if (subdir !in excludedFiles) {
                    childrenCount += 1
                    recordDirState(subdir)
                }
                FileVisitResult.SKIP_SUBTREE
            }

            onVisitFile { file, attrs ->
                if (file !in excludedFiles) {
                    childrenCount += 1
                    recordFileState(file, attrs)
                }
                FileVisitResult.CONTINUE
            }

            onPostVisitDirectory { _, exc ->
                if (exc != null) {
                    throw exc
                }
                FileVisitResult.CONTINUE
            }
        }

        if (childrenCount == 0) {
            stateByFilePath[dir.pathString] = "EMPTY DIR"
        }
    }

    private fun recordFileState(path: Path, attr: BasicFileAttributes) {
        val posixPart = if (attr is PosixFileAttributes) {
            " mode ${PosixUtil.toUnixMode(attr.permissions())} owner ${attr.owner().name} group ${attr.group().name}"
        } else ""
        stateByFilePath[path.pathString] = "size ${attr.size()} mtime ${attr.lastModifiedTime()}$posixPart"
    }
}

private fun getAttributes(path: Path): BasicFileAttributes? =
    // we assume that missing files are exceptional and usually all paths exist
    try {
        if (PosixUtil.isPosixFileSystem) {
            path.readAttributes<PosixFileAttributes>()
        } else {
            path.readAttributes<BasicFileAttributes>()
        }
    } catch (_: java.nio.file.NoSuchFileException) {
        null
    }
