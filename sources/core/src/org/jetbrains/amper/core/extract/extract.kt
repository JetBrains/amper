/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.core.extract

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.archivers.zip.ZipFile
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
import org.jetbrains.amper.concurrency.withDoubleLock
import org.jetbrains.amper.core.AmperUserCacheRoot
import org.jetbrains.amper.core.hashing.sha256String
import org.jetbrains.amper.filechannels.readText
import org.jetbrains.amper.filechannels.writeText
import org.slf4j.LoggerFactory
import java.io.IOException
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.FileTime
import java.nio.file.attribute.PosixFilePermissions
import java.time.Instant
import kotlin.io.path.Path
import kotlin.io.path.copyTo
import kotlin.io.path.createDirectories
import kotlin.io.path.createParentDirectories
import kotlin.io.path.createSymbolicLinkPointingTo
import kotlin.io.path.deleteRecursively
import kotlin.io.path.exists
import kotlin.io.path.inputStream
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.setLastModifiedTime
import kotlin.io.path.setPosixFilePermissions

suspend fun extractFileToCacheLocation(
    archiveFile: Path,
    amperUserCacheRoot: AmperUserCacheRoot,
    vararg options: ExtractOptions
): Path = withContext(Dispatchers.IO) {
    val cachePath = amperUserCacheRoot.extractCache
    val hash = "$archiveFile${getExtractOptionsShortString(options)}".sha256String().take(6)
    val directoryName = "${archiveFile.fileName}.${hash}.d"
    val targetDirectory = cachePath.resolve(directoryName)
    val flagFile = cachePath.resolve("${directoryName}.flag")
    extractFileWithFlag(archiveFile, targetDirectory, flagFile, *options)
}

// initially from intellij:community/platform/build-scripts/downloader/src/org/jetbrains/intellij/build/dependencies/BuildDependenciesDownloader.kt

/**
 * Assumes that [archiveFile] is immutable
 */
suspend fun extractFileToLocation(
    archiveFile: Path,
    targetDirectory: Path,
    vararg options: ExtractOptions
): Path = withContext(Dispatchers.IO) {
    val flagFile = targetDirectory.parent.resolve("${archiveFile.fileName}.flag")
    flagFile.createParentDirectories()
    extractFileWithFlag(archiveFile, targetDirectory, flagFile, *options)
}

private suspend fun extractFileWithFlag(
    archiveFile: Path,
    targetDirectory: Path,
    flagFile: Path,
    vararg options: ExtractOptions
): Path {
    withDoubleLock(flagFile) { fileChannel ->
        extractFileWithFlag(archiveFile, targetDirectory, fileChannel, flagFile, options)
    }
    return targetDirectory
}

private fun extractFileWithFlag(
    archiveFile: Path,
    targetDirectory: Path,
    flagChannel: FileChannel,
    flagFile: Path,
    options: Array<out ExtractOptions>,
) {
    if (checkFlagFile(archiveFile, flagChannel, targetDirectory, options)) {
        LOG.debug("Skipping extract to {} since flag file {} is correct", targetDirectory, flagFile)

        // Update file modification time to maintain FIFO caches i.e.
        // in persistent cache folder on TeamCity agent
        val now = FileTime.from(Instant.now())
        targetDirectory.setLastModifiedTime(now)
        flagFile.setLastModifiedTime(now)
        return
    }

    if (targetDirectory.exists()) {
        check(targetDirectory.isDirectory()) { "Target '$targetDirectory' exists, but it's not a directory. Please delete it manually" }
        cleanDirectory(targetDirectory)
    }

    LOG.info("Extracting $archiveFile to $targetDirectory")
    targetDirectory.createDirectories()

    val filesAfterCleaning = targetDirectory.listDirectoryEntries()
    check(filesAfterCleaning.isEmpty()) {
        "Target directory $targetDirectory is not empty after cleaning: ${filesAfterCleaning.joinToString(" ")}"
    }

    val start = ByteBuffer.allocate(4)
    FileChannel.open(archiveFile).use { channel -> channel.read(start, 0) }
    start.flip()
    check(start.remaining() == 4) { "File $archiveFile is smaller than 4 bytes, could not be extracted" }

    val stripRoot = options.any { it == ExtractOptions.STRIP_ROOT }
    val magicNumber = start.order(ByteOrder.LITTLE_ENDIAN).getInt(0)
    if (start[0] == 0x50.toByte() && start[1] == 0x4B.toByte()) {
        extractZip(archiveFile, targetDirectory, stripRoot)
    } else if (start[0] == 0x1F.toByte() && start[1] == 0x8B.toByte()) {
        extractTarGz(archiveFile, targetDirectory, stripRoot)
    } else if (start[0] == 0x42.toByte() && start[1] == 0x5A.toByte()) {
        extractTarBz2(archiveFile, targetDirectory, stripRoot)
    } else {
        error(
            "Unknown archive format at ${archiveFile}." +
                    " Magic number (little endian hex): ${Integer.toHexString(magicNumber)}." +
                    " Currently only .tar.gz/.zip/.bz2 are supported"
        )
    }

    val expectedFlagFileContent = getExpectedFlagFileContent(archiveFile, targetDirectory, options)

    flagChannel.truncate(0)
    flagChannel.position(0)
    flagChannel.writeText(expectedFlagFileContent)

    check(checkFlagFile(archiveFile, flagChannel, targetDirectory, options)) {
        "'checkFlagFile' must be true right after extracting the archive. flagFile:${flagFile} archiveFile:${archiveFile} target:${targetDirectory}"
    }
}

fun extractZip(archiveFile: Path, target: Path, stripRoot: Boolean) {
    ZipFile.builder().setSeekableByteChannel(FileChannel.open(archiveFile)).get().use { zipFile ->
        val entries = zipFile.entries
        genericExtract(archiveFile, object : ArchiveContent {
            override val nextEntry: Entry?
                get() {
                    if (!entries.hasMoreElements()) {
                        return null
                    }
                    val entry = entries.nextElement()
                    return object : Entry {
                        override val type: Entry.Type
                            get() = when {
                                entry.isUnixSymlink -> Entry.Type.SYMLINK
                                entry.isDirectory -> Entry.Type.DIR
                                else -> Entry.Type.FILE
                            }
                        override val name: String
                            get() = entry.name
                        override val isExecutable: Boolean
                            get() = entry.unixMode and octal_0111 != 0

                        @get:Throws(IOException::class)
                        override val linkTarget: String?
                            get() = zipFile.getUnixSymlink(entry)

                        @get:Throws(IOException::class)
                        override val inputStream: InputStream
                            get() = zipFile.getInputStream(entry)
                    }
                }
        }, target, stripRoot)
    }
}

private val octal_0111 = "111".toInt(8)

private val isWindows = System.getProperty("os.name").lowercase().startsWith("windows")

private fun extractTarBz2(archiveFile: Path, target: Path, stripRoot: Boolean) {
    extractTarBasedArchive(archiveFile, target, stripRoot) { BZip2CompressorInputStream(it) }
}

private fun extractTarGz(archiveFile: Path, target: Path, stripRoot: Boolean) {
    extractTarBasedArchive(archiveFile, target, stripRoot) { GzipCompressorInputStream(it) }
}

private fun extractTarBasedArchive(
    archiveFile: Path,
    target: Path,
    stripRoot: Boolean,
    decompressor: (InputStream) -> InputStream
) {
    TarArchiveInputStream(decompressor(archiveFile.inputStream().buffered())).use { archive ->
        genericExtract(archiveFile, object : ArchiveContent {
            @get:Throws(IOException::class)
            override val nextEntry: Entry?
                get() {
                    val entry = archive.nextEntry ?: return null
                    return object : Entry {
                        override val type: Entry.Type
                            get() = when {
                                entry.isSymbolicLink -> Entry.Type.SYMLINK
                                entry.isDirectory -> Entry.Type.DIR
                                entry.isFile -> Entry.Type.FILE
                                else -> error("${archiveFile}: unknown entry type at '${entry.name}")
                            }
                        override val name: String
                            get() = entry.name
                        override val isExecutable: Boolean
                            get() = entry.mode and octal_0111 != 0
                        override val linkTarget: String?
                            get() = entry.linkName
                        override val inputStream: InputStream
                            get() = CloseShieldInputStream(archive)
                    }
                }
        }, target, stripRoot)
    }
}

private interface ArchiveContent {
    val nextEntry: Entry?
}

private interface Entry {
    enum class Type { FILE, DIR, SYMLINK }

    val type: Type
    val name: String
    val isExecutable: Boolean
    val linkTarget: String?
    val inputStream: InputStream
}

private class EntryNameConverter(
    private val archiveFile: Path,
    private val target: Path,
    private val stripRoot: Boolean
) {
    private var leadingComponentPrefix: String? = null
    fun getOutputPath(entryName: String, isDirectory: Boolean): Path? {
        val normalizedName = normalizeEntryName(entryName)
        if (!stripRoot) {
            return target.resolve(normalizedName)
        }
        if (leadingComponentPrefix == null) {
            val split = normalizedName.split('/'.toString().toRegex(), limit = 2).toTypedArray()
            leadingComponentPrefix = split[0] + '/'
            return if (split.size < 2) {
                check(isDirectory) { "$archiveFile: first top-level entry must be a directory if strip root is enabled" }
                null
            } else {
                target.resolve(split[1])
            }
        }
        check(
            normalizedName.startsWith(
                leadingComponentPrefix!!
            )
        ) { "$archiveFile: entry name '$normalizedName' should start with previously found prefix '$leadingComponentPrefix'" }
        return target.resolve(normalizedName.substring(leadingComponentPrefix!!.length))
    }
}

private fun genericExtract(archiveFile: Path, archive: ArchiveContent, target: Path, stripRoot: Boolean) {
    val isPosixFs = target.fileSystem.supportedFileAttributeViews().contains("posix")

    // avoid extra createDirectories calls
    val createdDirs: MutableSet<Path> = HashSet()
    val converter = EntryNameConverter(archiveFile, target, stripRoot)
    val canonicalTarget = target.normalize()
    while (true) {
        val entry: Entry = archive.nextEntry ?: break
        val type: Entry.Type = entry.type
        val entryPath = converter.getOutputPath(entry.name, type == Entry.Type.DIR) ?: continue
        if (type == Entry.Type.DIR) {
            entryPath.createDirectories()
            createdDirs.add(entryPath)
        } else {
            val parent = entryPath.parent
            if (createdDirs.add(parent)) {
                parent.createDirectories()
            }
            if (type == Entry.Type.SYMLINK) {
                val relativeSymlinkTarget = Path(entry.linkTarget!!)
                val resolvedTarget = entryPath.resolveSibling(relativeSymlinkTarget).normalize()
                if (!resolvedTarget.startsWith(canonicalTarget) || resolvedTarget == canonicalTarget) {
                    LOG.debug(
                        """
  $archiveFile: skipping symlink entry '${entry.name}' which points outside of archive extraction directory, which is forbidden.
  resolved target = $resolvedTarget
  root = $canonicalTarget
  
  """.trimIndent()
                    )
                    continue
                }
                if (isWindows) {
                    // On Windows symlink creation is still gated by various registry keys
                    if (resolvedTarget.isRegularFile()) {
                        resolvedTarget.copyTo(entryPath, StandardCopyOption.REPLACE_EXISTING)
                    }
                } else {
                    entryPath.createSymbolicLinkPointingTo(relativeSymlinkTarget)
                }
            } else if (type == Entry.Type.FILE) {
                entry.inputStream.use { fs -> Files.copy(fs, entryPath, StandardCopyOption.REPLACE_EXISTING) }
                if (isPosixFs && entry.isExecutable) {
                    @Suppress("SpellCheckingInspection")
                    entryPath.setPosixFilePermissions(PosixFilePermissions.fromString("rwxr-xr-x"))
                }
            } else {
                error("Unknown entry type: $type")
            }
        }
    }
}

private fun normalizeEntryName(name: String): String {
    val normalized = name.replace('\\', '/').trim('/')
    assertValidEntryName(normalized)
    return normalized
}

private fun assertValidEntryName(normalizedEntryName: String) {
    check(normalizedEntryName.isNotBlank()) { "Entry names should not be blank" }
    check(!normalizedEntryName.contains('\\')) { "Normalized entry names should not contain '\\'" }
    check(!normalizedEntryName.startsWith('/')) { "Normalized entry names should not start with '/': $normalizedEntryName" }
    check(!normalizedEntryName.endsWith('/')) { "Normalized entry names should not end with '/': $normalizedEntryName" }
    check(!normalizedEntryName.contains("//")) { "Normalized entry name should not contain '//': $normalizedEntryName" }
    check(
        !(normalizedEntryName.contains("..") && normalizedEntryName.split('/').contains(".."))
    ) { "Invalid entry name: $normalizedEntryName" }
}

// increment on semantic changes in extract code to invalidate all current caches
private const val EXTRACT_CODE_VERSION = 4

private fun getExpectedFlagFileContent(
    archiveFile: Path,
    targetDirectory: Path,
    options: Array<out ExtractOptions>
): String {
    val numberOfTopLevelEntries = targetDirectory.listDirectoryEntries().size
    return """$EXTRACT_CODE_VERSION
${archiveFile.toRealPath(LinkOption.NOFOLLOW_LINKS)}
topLevelEntries:$numberOfTopLevelEntries
options:${getExtractOptionsShortString(options)}
"""
}

fun cleanDirectory(directory: Path) {
    directory.createDirectories()
    directory.listDirectoryEntries().forEach {
        it.deleteRecursively()
    }
}

private fun checkFlagFile(
    archiveFile: Path,
    flagChannel: FileChannel,
    targetDirectory: Path,
    options: Array<out ExtractOptions>
): Boolean {
    if (!targetDirectory.isDirectory()) {
        return false
    }
    return flagChannel.readText() == getExpectedFlagFileContent(archiveFile, targetDirectory, options)
}

private fun getExtractOptionsShortString(options: Array<out ExtractOptions>): String {
    if (options.isEmpty()) {
        return ""
    }
    val sb = mutableSetOf<Char>()
    for (option in options) {
        when (option) {
            ExtractOptions.STRIP_ROOT -> sb.add('s')
        }
    }
    return sb.joinToString("")
}

enum class ExtractOptions {
    /**
     * Strip leading component from file names on extraction.
     * Asserts that the leading component is the same for every file
     */
    STRIP_ROOT
}

private val LOG = LoggerFactory.getLogger("extract")
