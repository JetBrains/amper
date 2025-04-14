/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */
package org.jetbrains.amper.filechannels

import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.Path
import java.nio.file.StandardOpenOption

/**
 * Reads the entire content of this [FileChannel] as a UTF-8 string.
 *
 * Malformed byte sequences are replaced by the replacement char `\uFFFD`.
 */
fun FileChannel.readText() = readBytes().decodeToString()

/**
 * Reads the entire content of this [FileChannel] into a [ByteArray].
 */
fun FileChannel.readBytes(): ByteArray {
    position(0)

    val size = size()
    if (size == 0L) {
        return ByteArray(0)
    }
    if (size > Int.MAX_VALUE) {
        error("this file is too big to fit in a ByteArray")
    }

    val buf = ByteArray(size.toInt())
    val bb = ByteBuffer.wrap(buf)

    while (bb.remaining() > 0) {
        val n = read(bb)
        if (n <= 0) {
            error("no bytes read from file")
        }
    }
    return buf
}

/**
 * Writes the given [text] encoded as UTF-8 to this [FileChannel] at the current position.
 */
fun FileChannel.writeText(text: String) {
    writeBytes(text.encodeToByteArray())
}

/**
 * Writes the given [bytes] to this [FileChannel] at the current position.
 */
fun FileChannel.writeBytes(bytes: ByteArray) {
    writeFully(ByteBuffer.wrap(bytes))
}

/**
 * Writes all bytes from the given [buffer] to this [FileChannel].
 *
 * The built-in [FileChannel.write] method doesn't guarantee that all bytes from the buffer are written to the file in a
 * single call, whereas this method does.
 */
fun FileChannel.writeFully(buffer: ByteBuffer) {
    while (buffer.remaining() > 0) {
        val n = write(buffer)
        if (n <= 0) {
            error("no bytes written")
        }
    }
}

/**
 * Writes the content of the given [source] file to this [FileChannel].
 */
fun FileChannel.writeFrom(source: Path) {
    FileChannel.open(source, StandardOpenOption.READ).use { channel ->
        this.transferFrom(channel, 0, channel.size())
    }
}
