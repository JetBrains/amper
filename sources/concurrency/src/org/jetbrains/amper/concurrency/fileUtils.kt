/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.concurrency

import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.channels.ReadableByteChannel
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.security.MessageDigest

fun interface Writer {
    fun write(data: ByteBuffer)
}
class Hasher(algorithm: String) {
    private val digest = MessageDigest.getInstance(algorithm)
    val algorithm: String = digest.algorithm
    val writer: Writer = Writer(digest::update)
    val hash: String by lazy { digest.digest().toHex() }
}

@OptIn(ExperimentalStdlibApi::class)
fun ByteArray.toHex() = toHexString()

fun computeHash(path: Path, hashers: List<Hasher>): Collection<Hasher> {
    FileChannel.open(path, StandardOpenOption.READ).use { channel ->
        channel.readTo(hashers.map { it.writer })
    }
    return hashers
}

fun ReadableByteChannel.readTo(writers: Collection<Writer>): Long {
    var size = 0L
    val data = ByteBuffer.allocate(1024)
    while (read(data) != -1) {
        writers.forEach {
            data.flip()
            it.write(data)
        }
        size += data.position()
        data.clear()
    }
    return size
}
