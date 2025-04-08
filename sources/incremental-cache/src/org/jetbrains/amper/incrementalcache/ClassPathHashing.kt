/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.incrementalcache

import java.io.File
import java.io.InputStream
import java.nio.file.Path
import java.security.MessageDigest
import kotlin.collections.forEach
import kotlin.io.path.Path
import kotlin.io.path.inputStream
import kotlin.io.path.isDirectory
import kotlin.io.path.listDirectoryEntries

/**
 * Computes the hash of all files in the current classpath.
 * This is a good way to identify the currently running code, and invalidate caches based on it.
 *
 * The hash algorithm used is specified by the [algorithm] parameter.
 *
 * @param algorithm The name of the hash algorithm to use.
 * @return The hash of the classpath.
 */
fun computeClassPathHash(algorithm: String = "md5"): String {
    val classPath = System.getProperty("java.class.path").ifEmpty { null } ?: return "empty"
    val classPathFiles = classPath.split(File.pathSeparator).map { Path(it) }
    return hashFiles(classPathFiles, algorithm)
}

/**
 * Computes the hash of the given [files] contents, using the given [algorithm].
 */
@OptIn(ExperimentalStdlibApi::class)
private fun hashFiles(files: List<Path>, algorithm: String = "md5"): String {
    val hasher = MessageDigest.getInstance(algorithm)
    hasher.update(files)
    return hasher.digest().toHexString()
}

private fun MessageDigest.update(files: List<Path>) {
    files.forEach { update(it) }
}

private fun MessageDigest.update(file: Path) {
    if (file.isDirectory()) {
        update(file.listDirectoryEntries())
    } else {
        file.inputStream().use { update(it) }
    }
}

private fun MessageDigest.update(data: InputStream) {
    val buffer = ByteArray(1024)
    var read = data.read(buffer)
    while (read > -1) {
        update(buffer, 0, read)
        read = data.read(buffer)
    }
}
