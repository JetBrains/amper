/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.jar

import kotlinx.serialization.Serializable
import java.nio.file.Path
import java.nio.file.attribute.BasicFileAttributes
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipEntry.STORED
import java.util.zip.ZipOutputStream
import kotlin.io.path.PathWalkOption
import kotlin.io.path.div
import kotlin.io.path.inputStream
import kotlin.io.path.isDirectory
import kotlin.io.path.outputStream
import kotlin.io.path.readAttributes
import kotlin.io.path.relativeTo
import kotlin.io.path.walk

/**
 * Compression strategy for zip entries.
 */
@Serializable
sealed class CompressionStrategy {
    /** Compress all entries */
    @Serializable
    data object CompressAll : CompressionStrategy()

    /** Store all entries uncompressed */
    @Serializable
    data object StoreAll : CompressionStrategy()

    /** Compress most entries but store specific entries (like lib/ for Spring Boot) uncompressed */
    @Serializable
    data class Selective(
        /**
         * Patterns for entry paths that should be stored uncompressed.
         * These should be in regex format and will be matched against the path of each entry relative to the root of the zip.
         */
        val uncompressedPatterns: List<String>,
    ) : CompressionStrategy()
}

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

    /**
     * Defines the compression strategy for entries in the archive.
     * Default is CompressAll for regular zip/jar files.
     * Use SELECTIVE for Spring Boot executable jars to keep lib/ entries uncompressed.
     * Use STORE_ALL if all entries should be stored without compression.
     */
    val compressionStrategy: CompressionStrategy = CompressionStrategy.CompressAll,
)

/**
 * An input file or directory to place in a zip file.
 */
data class ZipInput(
    /**
     * The path to this input file or directory to add to the zip.
     */
    val path: Path,
    /**
     * The corresponding path in the zip, relative to the root of the zip.
     *
     * If [path] is a file, then [destPathInArchive] is the destination path of the file in the zip.
     *
     * If [path] is a directory, all children will recursively be placed in the [destPathInArchive] directory in the
     * zip. The destination path of each file relative to [destPathInArchive] is the same as their original path
     * relative to [path].
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
 * Files and directories are added to the archive in an order that depends on [ZipConfig.reproducibleFileOrder].
 */
fun Path.writeZip(inputs: List<ZipInput>, config: ZipConfig = ZipConfig()) {
    ZipOutputStream(outputStream()).use { out ->
        out.writeZip(inputs, config)
    }
}

/**
 * Writes all files from the given [inputs] (recursively) to this [ZipOutputStream].
 * Parts of this process can be configured using the given [config].
 *
 * Files and directories are added to the archive in an order that depends on [ZipConfig.reproducibleFileOrder].
 */
internal fun ZipOutputStream.writeZip(inputs: List<ZipInput>, config: ZipConfig) {
    inputs
        .asSequence()
        .flatMap { it.findEntriesToAdd() }
        .sortedIf(config.reproducibleFileOrder)
        .distinctBy { it.entryName }
        .forEach { spec ->
            writeZipEntry(spec.entryName, spec.inputFile, config)
        }
}

/**
 * Finds all the entries that should be included from this [ZipInput].
 * If this input is a file, then just one entry is returned for this file.
 * If this input is a directory, it is recursively traversed, and entries are returned for each file or directory.
 */
private fun ZipInput.findEntriesToAdd(): Sequence<ZipEntrySpec> = path
    .walk(PathWalkOption.INCLUDE_DIRECTORIES)
    .map { ZipEntrySpec(entryName = entryNameFor(it), inputFile = it) }
    .filter { it.entryName != "META-INF/MANIFEST.MF" } // we write manifest from our configuration, so explicitly drop it off
    .filter { it.entryName != "/" } // the top-level dir doesn't need to be registered as an entry

private fun ZipInput.entryNameFor(child: Path): String {
    val entryPathInZip = destPathInArchive / child.relativeTo(path)
    val entryName = entryPathInZip.normalize().joinToString("/") // ensures / is used even on Windows
    return if (child.isDirectory()) "$entryName/" else entryName
}

private data class ZipEntrySpec(val entryName: String, val inputFile: Path)

private fun Sequence<ZipEntrySpec>.sortedIf(condition: Boolean): Sequence<ZipEntrySpec> =
    if (condition) sortedBy { it.entryName } else this

/**
 * Determines if an entry should be stored uncompressed based on the compression strategy.
 */
private fun CompressionStrategy.shouldStoreUncompressed(entryName: String): Boolean = when (this) {
    CompressionStrategy.StoreAll -> true
    CompressionStrategy.CompressAll -> false
    is CompressionStrategy.Selective -> this.uncompressedPatterns.any { entryName.matches(it.toRegex()) }
}

/**
 * Writes the contents of the given [file] as a new zip entry called [entryName].
 */
private fun ZipOutputStream.writeZipEntry(entryName: String, file: Path, config: ZipConfig) {
    val zipEntry = ZipEntry(entryName)

    if (config.compressionStrategy.shouldStoreUncompressed(entryName) && !file.isDirectory()) {
        // For uncompressed entries, we need to pre-calculate CRC and size
        zipEntry.method = STORED
        // STORED method requires calculating the size and crc32
        val attributes = file.readAttributes<BasicFileAttributes>()
        zipEntry.size = attributes.size()
        zipEntry.crc = file.crc32
    }

    if (config.preserveFileTimestamps) {
        val fileAttributes = file.readAttributes<BasicFileAttributes>()
        zipEntry.creationTime = fileAttributes.creationTime()
        zipEntry.lastAccessTime = fileAttributes.lastAccessTime()
        zipEntry.lastModifiedTime = fileAttributes.lastModifiedTime()
    }

    putNextEntry(zipEntry)

    if (!file.isDirectory()) {
        writeFileContents(file)
    }

    closeEntry()
}

private val Path.crc32: Long get() {
    val crc = CRC32()
    inputStream().use { input ->
        val buffer = ByteArray(8192)
        var bytesRead: Int
        while (input.read(buffer).also { bytesRead = it } != -1) {
            crc.update(buffer, 0, bytesRead)
        }
    }
    return crc.value
}

private fun ZipOutputStream.writeFileContents(file: Path) {
    file.inputStream().use {
        it.copyTo(this)
    }
}