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
import kotlin.io.path.readText

private val logger = LoggerFactory.getLogger("fileUtils.kt")

fun interface Writer {
    fun write(data: ByteBuffer)
}
class Hasher(algorithm: String): Hash {
    private val digest = MessageDigest.getInstance(algorithm)
    override val algorithm: String = digest.algorithm
    val writer: Writer = Writer(digest::update)
    override val hash: String by lazy { digest.digest().toHex() }
}

interface Hash {
    val algorithm: String
    val hash: String
}

data class SimpleHash(
    override val hash: String,
    override val algorithm: String
) : Hash

@OptIn(ExperimentalStdlibApi::class)
fun ByteArray.toHex() = toHexString()

suspend fun computeHash(path: Path, algorithm: String): Hasher = computeHash(path) { listOf(Hasher(algorithm)) }.single()

suspend fun computeHash(path: Path, hashersFn:() -> List<Hasher>): Collection<Hasher> =
    fileChannelReadOperationWithRetry(
        path,
        { e -> retryFileOperationOnException(e, path) }
    ) { fileChannel ->
        hashersFn().also { hashers ->
            fileChannel.readTo(hashers.map { it.writer })
        }
    }

suspend fun Path.readTextWithRetry(): String =
    fileOperationWithRetry(this) { it.readText() }

suspend fun <T> fileChannelReadOperationWithRetry(
    path: Path,
    retryOnException: (e: Exception) -> Boolean = { e -> retryFileOperationOnException(e, path) },
    block:(FileChannel) -> T
): T =
    fileOperationWithRetry(path, retryOnException) { _ ->
        FileChannel.open(path, StandardOpenOption.READ)
            .use { block(it) }
    }

suspend fun <T> fileOperationWithRetry(
    path: Path,
    retryOnException: (e: Exception) -> Boolean = { e -> retryFileOperationOnException(e, path) },
    block:(Path) -> T
): T {
    return withRetry(retryOnException = retryOnException) {
        withContext(Dispatchers.IO) {
            block(path)
        }
    }
}

suspend fun <T> withRetryOnAccessDenied(block: suspend () -> T): T = withRetry(
    retryOnException = { e ->
        when(e) {
            is java.nio.file.AccessDeniedException -> true
            else -> false
        }
    },
    block = block
)

fun retryFileOperationOnException(e: Exception, path: Path): Boolean =
    when (e) {
        // File doesn't exist - nothing to operate on.
        is NoSuchFileException -> false

        is IOException -> {
            // Retry until the file could be opened.
            // It could have been exclusively locked by DR for a very short period of time:
            // after downloaded file was moved from temp to target location (and thus became discoverable),
            // and before the process released file lock on that file (lock is hold on file moving)
            logger.debug(
                "File operation was interrupted by {}: {} ({})",
                e::class.simpleName, e.message, path
            )

            true
        }

        else -> false
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
