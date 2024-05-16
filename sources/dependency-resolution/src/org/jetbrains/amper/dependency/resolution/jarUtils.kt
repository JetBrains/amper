/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.dependency.resolution

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStreamReader
import java.nio.channels.Channels
import java.nio.channels.FileChannel
import java.nio.file.Path
import java.util.*
import java.util.jar.JarEntry
import java.util.jar.JarFile
import java.util.jar.JarInputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream


internal suspend fun readJarEntry(jarPath: Path, entryPath: String) : String? =
    withJarEntry(jarPath, entryPath) { jarFile, jarEntry ->
        jarFile.getInputStream(jarEntry).use { inputStream ->
            InputStreamReader(inputStream).readText()
        }
    }

internal suspend fun hasJarEntry(jarPath: Path, entryPath: String) : Boolean? =
    withJarEntry(jarPath, entryPath) { _, _ -> true }

internal suspend fun <T> withJarEntry(jarPath: Path, entryPath: String, block: (JarFile, JarEntry) -> T) : T? =
    withContext(Dispatchers.IO) {
        JarFile(jarPath.toFile()).use { jarFile ->
            val source = JarInputStream(FileInputStream(jarPath.toFile()))
            source.use {
                var entry: JarEntry?
                do {
                    entry = source.nextJarEntry
                } while (entry != null && entry.let { if (it.isDirectory) it.name.trimEnd('/') else it.name } != entryPath)

                entry?.let {
                    block(jarFile, it)
                }
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
                JarFile(srcJar.toFile()).use { jarFile ->
                    val entries: Enumeration<out ZipEntry?> = jarFile.entries()
                    while (entries.hasMoreElements()) {
                        val entry = entries.nextElement()!!

                        if (!entry.name.startsWith("$jarEntryDir/")) {
                            continue
                        } else {
                            val newEntryName = entry.name.removePrefix("$jarEntryDir/")

                            if (newEntryName.isBlank() || !writtenPaths.add(newEntryName))
                                continue

                            if (entry.isDirectory) {
                                addDirectoryEntry(out, newEntryName)
                            } else {
                                copyJarEntry(jarFile, entry, newEntryName, out, buffer)
                            }
                        }
                    }
                    out.finish()
                }
            }
        }
    }
}

private fun copyJarEntry(
    jarFile: JarFile,
    sourceEntry: ZipEntry,
    newEntryName: String,
    out: ZipOutputStream,
    buffer: ByteArray
) {
    jarFile.getInputStream(sourceEntry).use { inputStream ->
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
            val read = inputStream.read(buffer)
            if (read < 0) break
            out.write(buffer, 0, read)
        }
        out.closeEntry()
    }
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
