/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.concurrency

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.channels.ReadableByteChannel
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.security.MessageDigest

internal val logger = LoggerFactory.getLogger("fileUtils.kt")

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

suspend fun computeHash(path: Path, hashersFn:() -> List<Hasher>): Collection<Hasher> {
    return withRetry(
        retryOnException = {
            when(it) {
                // File doesn't exist - nothing to compute hash from, rethrow further.
                is NoSuchFileException -> false

                is IOException -> {
                    // Retry until the file could be opened.
                    // It could have been exclusively locked by DR for a very short period of time:
                    // after downloaded file was moved from temp to target location (and thus became discoverable),
                    // and before the process released file lock on that file (lock is hold on file moving)
                    logger.debug("Cache computation was interrupted by ${it::class.simpleName}: ${it.message}")

                    true
                }
                else -> false
            }
        }
    ) {
        val hashers = hashersFn()
        withContext(Dispatchers.IO) {
            FileChannel.open(path, StandardOpenOption.READ)
        }.use { channel ->
            channel.readTo(hashers.map { it.writer })
        }
        return@withRetry hashers
    }
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
