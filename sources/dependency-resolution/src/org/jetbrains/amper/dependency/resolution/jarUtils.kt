/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.dependency.resolution

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.nio.channels.Channels
import java.nio.channels.FileChannel
import java.nio.file.Path
import java.util.jar.JarEntry
import java.util.jar.JarFile
import java.util.jar.JarInputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream


internal suspend fun readJarEntry(jarPath: Path, entryPath: String) : String? =
    withJarEntry(jarPath, entryPath) { jarInputStream ->
        InputStreamReader(jarInputStream).use {
            it.readText()
        }
    }

internal suspend fun hasJarEntry(jarPath: Path, entryPath: String) : Boolean? =
    withJarEntry(jarPath, entryPath) { _ -> true }

/**
 * Find an entry with the given [entryPath] in the given [jarPath] and run the lambda [block] on it.
 *
 * @return null if the entry is not found, and result of the given [block] otherwise
 */
suspend fun <T> withJarEntry(jarPath: Path, entryPath: String, block: suspend (InputStream) -> T) : T? =
    withContext(Dispatchers.IO) {
        val jarFile = jarPath.toJarFile()
        jarFile.use {
            val jarEntry: JarEntry = jarFile.getJarEntry(entryPath) ?: return@withContext null
            jarFile.getInputStream(jarEntry).use {
                block(it)
            }
        }
    }

/**
 * It takes a subfolder with the path <code>jarEntryDir<code> from given <code>srcJar<code>,
 *     copy its content right into the root of a new Jar archive,
 *     and then writes resulted Jar archive to the given FileChannel.
 */
@Throws(Exception::class)
suspend fun copyJarEntryDirToJar(fileChannel: FileChannel, jarEntryDir: String, srcJar: Path) {
    withContext(Dispatchers.IO) {
        // write subfolder of
        Channels.newOutputStream(fileChannel).also { stream ->
            // there is no need to close the output stream
            // since it is channel-based, and the channel itself is closed on the caller side
            ZipOutputStream(stream).also { out ->
                val buffer = ByteArray(4096)

                val writtenPaths = mutableSetOf<String>()
                JarInputStream(FileInputStream(srcJar.toFile())).use { jarInputStream ->
                    var entry: JarEntry? = jarInputStream.nextJarEntry

                    while (entry != null) {
                        if (entry.name.startsWith("$jarEntryDir/")) {
                            val newEntryName = entry.name.removePrefix("$jarEntryDir/")
                            if (!newEntryName.isBlank() && writtenPaths.add(newEntryName)) {
                                if (entry.isDirectory) {
                                    addDirectoryEntry(out, newEntryName)
                                } else {
                                    copyJarEntry(jarInputStream, entry, newEntryName, out, buffer)
                                }
                            }
                        }
                        entry = jarInputStream.nextJarEntry
                    }
                    out.finish()
                }
            }
        }
    }
}

private fun copyJarEntry(
    jarInputStream: JarInputStream,
    sourceEntry: ZipEntry,
    newEntryName: String,
    out: ZipOutputStream,
    buffer: ByteArray
) {
    val newEntry = JarEntry(newEntryName)

    if (sourceEntry.method == JarEntry.STORED) {
        newEntry.method = ZipEntry.STORED
        newEntry.size = sourceEntry.size
        newEntry.crc = sourceEntry.crc
    }
    newEntry.time = System.currentTimeMillis()
    newEntry.extra = sourceEntry.extra

    out.putNextEntry(newEntry)

    while (true) {
        val read = jarInputStream.read(buffer)
        if (read < 0) break
        out.write(buffer, 0, read)
    }
    out.closeEntry()
}

@Throws(IOException::class)
private fun addDirectoryEntry(output: ZipOutputStream, relativePath: String) {
    val e = ZipEntry(relativePath)
    e.method = ZipEntry.STORED
    e.size = 0
    e.crc = 0
    output.putNextEntry(e)
    output.closeEntry()
}

fun Path.toJarFile() = JarFile(toFile())