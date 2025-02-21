/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.jar

import kotlinx.serialization.Serializable
import java.nio.file.Path
import java.nio.file.attribute.BasicFileAttributes
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.io.path.PathWalkOption
import kotlin.io.path.div
import kotlin.io.path.inputStream
import kotlin.io.path.isDirectory
import kotlin.io.path.outputStream
import kotlin.io.path.pathString
import kotlin.io.path.readAttributes
import kotlin.io.path.relativeTo
import kotlin.io.path.walk

typealias ZipEntryName = String

@Serializable
data class ZipConfig(
    /**
     * Ensures the files are written to the archive in a consistent order that's independent of the file system or OS,
     * typically by sorting the file paths alphabetically.
     * This must be enabled in order to get reproducible archives.
     */
    val reproducibleFileOrder: Boolean = true,
    /**
     * Copies the original file timestamps (creation, last access, last modification) to the zip entries.
     * This must be disabled in order to get reproducible archives.
     */
    val preserveFileTimestamps: Boolean = false,
)

/**
 * An input directory for a zip creation.
 */
data class ZipInput(
    /**
     * The path to this input directory (where the input files are located) or a file.
     */
    val path: Path,
    /**
     * The directory where the file(s) should be placed in the zip, relative to the root of the zip.
     */
    val destPathInArchive: Path,
) {
    init {
        require(!destPathInArchive.isAbsolute) {
            "destPathInArchive must be a relative path (relative to the root of the zip), got '$destPathInArchive'"
        }
    }
}

/**
 * Creates a zip file at this [Path] and writes all files from the given [inputs] into that zip.
 * Parts of this process can be configured using the given [config].
 *
 * The inputs are added to the zip in the order of the given [inputs] list.
 * Files within an input directory are added to the archive in an order that depends on the given [config].
 */
fun Path.writeZip(inputs: List<ZipInput>, config: ZipConfig = ZipConfig()) {
    ZipOutputStream(outputStream()).use { out ->
        out.writeZip(inputs, config)
    }
}

internal fun ZipOutputStream.writeZip(inputDirs: List<ZipInput>, config: ZipConfig) {
    var entriesWritten = setOf<ZipEntryName>()
    inputDirs.forEach { input ->
        entriesWritten = writeInput(input, config, entriesWritten)
    }
}

/**
 * Writes all files from the given [input] (recursively) to this [ZipOutputStream].
 * The path of the files within the zip is their original path relative to [input].
 */
private fun ZipOutputStream.writeInput(input: ZipInput, config: ZipConfig, entriesWritten: Set<ZipEntryName> = setOf()): Set<ZipEntryName> {
    val filesToRelativePaths: Sequence<Pair<Path, Path>> = when {
        input.path.isDirectory() -> input
            .path
            .walk(PathWalkOption.INCLUDE_DIRECTORIES)
            .sortedIf(config.reproducibleFileOrder).map {
                it to it.relativeTo(input.path)
            }
            .filter { it.second.pathString != "" }

        else -> sequenceOf(input.path to input.path.fileName)
    }
    return buildSet {
        addAll(entriesWritten)
        filesToRelativePaths.forEach { (file, relativePathInInputDir) ->
            val entryPathInZip = input.destPathInArchive / relativePathInInputDir
            val entryName = entryPathInZip.normalize().joinToString("/") // ensures / is used even on Windows
            val normalizedEntryName = if (file.isDirectory()) "$entryName/" else entryName
            if (normalizedEntryName in this) return@forEach
            writeZipEntry(entryName = normalizedEntryName, file = file, config)
            add(normalizedEntryName)
        }
    }
}

// sorting the Paths themselves is not consistent across OS-es, we need to use the pathStrings
private fun Sequence<Path>.sortedIf(condition: Boolean): Sequence<Path> =
    if (condition) sortedBy { it.pathString } else this

/**
 * Writes the contents of the given [file] as a new zip entry called [entryName].
 */
private fun ZipOutputStream.writeZipEntry(entryName: String, file: Path, config: ZipConfig) {
    val zipEntry = ZipEntry(entryName)
    if (config.preserveFileTimestamps) {
        val fileAttributes = file.readAttributes<BasicFileAttributes>()
        zipEntry.creationTime = fileAttributes.creationTime()
        zipEntry.lastAccessTime = fileAttributes.lastAccessTime()
        zipEntry.lastModifiedTime = fileAttributes.lastModifiedTime()
    }
    putNextEntry(zipEntry)
    writeFile(file)
    closeEntry()
}

private fun ZipOutputStream.writeFile(file: Path) {
    if (file.isDirectory()) return
    file.inputStream().use {
        it.copyTo(this)
    }
}
