/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.concurrency

import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.sync.Mutex
import org.slf4j.LoggerFactory
import java.io.IOException
import java.nio.channels.AsynchronousCloseException
import java.nio.channels.ClosedChannelException
import java.nio.channels.FileChannel
import java.nio.channels.FileLock
import java.nio.channels.FileLockInterruptionException
import java.nio.channels.NonWritableChannelException
import java.nio.channels.OverlappingFileLockException
import java.nio.file.AccessDeniedException
import java.nio.file.NoSuchFileException
import java.nio.file.OpenOption
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.*
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract
import kotlin.coroutines.coroutineContext
import kotlin.io.path.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.deleteIfExists
import kotlin.io.path.name
import kotlin.math.min
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

private val logger = LoggerFactory.getLogger("fileLock.kt")

/**
 * A default [StripedMutex] to use for file-based double-locking.
 */
val DefaultFilesMutex = StripedMutex(stripeCount = 512)

/**
 * Executes the given [block] under a double lock:
 * * a non-reentrant coroutine [Mutex] based on the [lockFile]'s path, getting exclusive access inside the current JVM
 * * a FileChannel lock on the given [lockFile], getting exclusive access across all processes on the system
 *
 * Both locks are unlocked after the method returns.
 *
 * The [block]'s parameter is the [FileChannel] used to lock the given [lockFile], if further inspection of the file is
 * needed while under the lock. The lock can be read and/or written to depending on the given open [options].
 *
 * Note: Since the first lock is a non-reentrant coroutine Mutex, callers MUST NOT call `withDoubleLock` again from
 * inside the given [block], that would make the current coroutine hang.
 * If an [owner] object is given, the owner's identity is used to detect such issues and eagerly fail instead of
 * hanging.
 */
suspend fun <T> StripedMutex.withDoubleLock(
    lockFile: Path,
    owner: Any? = null,
    options: Array<out OpenOption> = arrayOf(
        StandardOpenOption.READ,
        StandardOpenOption.WRITE,
        StandardOpenOption.CREATE,
    ),
    block: suspend (lockFileChannel: FileChannel) -> T,
): T {
    contract {
        callsInPlace(block, InvocationKind.EXACTLY_ONCE)
    }
    // The first lock locks the stuff inside one JVM process
    val localMutexKey = lockFile.toAbsolutePath().normalize().hashCode()
    return withLock(localMutexKey, owner) {
        // The second lock locks a flagFile across all processes on the system
        lockFile.withFileChannelLock(*options) {
            block(it)
        }
    }
}

/**
 * Executes the given [block] under a system-wide exclusive lock on the file at this [Path].
 *
 * The [block]'s parameter is the [FileChannel] used to lock this file, if further inspection of the file is needed
 * while under the lock. The lock can be read and/or written to depending on the given open [options].
 *
 * If a [FileChannel] cannot be opened because of access errors, the open operation is retried several times.
 * This is to remediate the fact that sometimes the path stays inaccessible for a short time after being removed.
 *
 * This function blocks until the file can be locked or the current coroutine is canceled, whichever comes first.
 * If a "resource deadlock avoided" exception is thrown, this function suspends and retries several times.
 *
 * File locks are held on behalf of the entire Java virtual machine. They are not suitable for controlling access to a
 * file by multiple threads within the same virtual machine.
 *
 * @throws OverlappingFileLockException If a lock that overlaps the requested region is already held by this Java
 *         virtual machine, or if another thread is already blocked in this function and is attempting to lock an
 *         overlapping region of the same file
 * @throws NonWritableChannelException If this file was not opened for writing
 * @throws IOException If some other I/O error occurs
 */
private suspend inline fun <T> Path.withFileChannelLock(vararg options: OpenOption, block: (FileChannel) -> T): T {
    contract {
        callsInPlace(block, InvocationKind.EXACTLY_ONCE)
    }
    // Files can sometimes be inaccessible for a short time right after a removal.
    val lockFileChannel = withRetry(retryOnException = { it is AccessDeniedException }) {
        FileChannel.open(this, *options)
    }
    return lockFileChannel.use { fileChannel ->
        fileChannel.lockWithRetry().use {
            block(fileChannel)
        }
    }
}

/**
 * Create a target file once and reuse it as a result of further invocations until it is resolved and returned by
 *  the given function [getAlreadyProducedResult]
 *
 * Method could be used safely from different JVM threads and from different processes.
 *
 * To achieve such concurrency safety, it does the following:
 * - creates a temporary lock file inside the given temp directory ([tempDir]),
 * - takes JVM and inter-process locks on that file
 * - creates a temporary file inside temp directory
 * - under the lock write content to that file with help of the given lambda ([block])
 * - finally, temporary lock file is removed
 * - (temporary file with content is removed as well, but only in case exception was thrown from [block],
 *    otherwise it is a responsibility of given <code>block<code> to remove temp file)
 *
 * The following two restrictions are applied on the input parameters for correct locking logic:
 *  - __MUST__ the same temporary directory is used for the given target file no matter
 *      how many times the method is called (in case a target file is produced by the given [block]);
 *  - __SHOULD__ different target files with the same file names correspond to different temp directories
 *      (if this is not met, some contention might be observed during downloading of such files)
 *
 * Note: Since the first lock is a non-reentrant coroutine [kotlinx.coroutines.sync.Mutex],
 *  callers MUST not call locking methods defined in this utility file again from inside the lambda
 *  ([block]) — that would lead to the hanging coroutine.
 */
suspend fun <T> produceResultWithDoubleLock(
    tempDir: Path,
    targetFileName: String,
    fileLockSource: StripedMutex? = null,
    getAlreadyProducedResult: suspend () -> T?,
    block: suspend (Path, FileChannel) -> T,
): T {
    // returns the already produced file without locking if allowed, otherwise proceed with file production
    getAlreadyProducedResult()?.let { return it }

    // todo (AB) : Maybe store it in <storage.root>/lock and never remove? (in order to resolve deletion failures attempts)
    val tempLockFile = tempLockFile(tempDir, targetFileName)

    // The first lock locks the stuff inside one JVM process
    return (fileLockSource ?: DefaultFilesMutex).withLock(tempLockFile.hashCode()) {
        // The second lock locks a flagFile across all processes on the system
        produceResultWithFileLock(tempDir, targetFileName, block, getAlreadyProducedResult)
    }
}

private suspend fun <T> produceResultWithFileLock(
    tempDir: Path,
    targetFileName: String,
    block: suspend (Path, FileChannel) -> T,
    getAlreadyProducedResult: suspend () -> T?
): T {
    getAlreadyProducedResult()?.let { return it }

    val tempLockFile = tempLockFile(tempDir, targetFileName)
    tempLockFile.parent.createDirectories()

    while (true) {
        return try {
            // Open a temporary lock file channel
            tempLockFile.withFileChannelLock(StandardOpenOption.WRITE, StandardOpenOption.CREATE) {
                logger.trace(
                    "### ${System.currentTimeMillis()} {} {} : locked, getAlreadyProducedResult()={}",
                    tempLockFile.hashCode(), tempLockFile.name, getAlreadyProducedResult()
                )
                try {
                    produceResultWithTempFile(tempDir, targetFileName, getAlreadyProducedResult, block)
                } finally {
                    tempLockFile.deleteIfExistsWithLogging("Temp lock file was deleted under the lock")
                }
            }.also {
                logger.trace(
                    "### ${System.currentTimeMillis()} {} {}: unlocked",
                    tempLockFile.hashCode(),
                    tempLockFile.name
                )
            }
        } catch (e: NoSuchFileException) {
            // Another process deleted the temp file before we were able to get the lock on it => Try again.
            logger.debug("NoSuchFileException from withFileChannelLock on {}", e.file, e)
            continue
        } catch (e: ClosedChannelException) {
            // Another process deleted the temp file before we were able to get the lock on it => Try again.
            logger.debug("ClosedChannelException from withFileChannelLock on {}", targetFileName, e)
            continue
        }
    }
}

suspend fun <T> produceResultWithTempFile(
    tempDir: Path,
    targetFileName: String,
    getAlreadyProducedResult: suspend () -> T?,
    block: suspend (Path, FileChannel) -> T,
): T {
    val tempFileNameSuffix = UUID.randomUUID().toString().let { it.substring(0, min(8, it.length)) }
    val tempFile = tempDir.resolve("~${targetFileName}.$tempFileNameSuffix")

    return try {
        getAlreadyProducedResult()
            ?: run {
                FileChannel.open(
                    tempFile,
                    StandardOpenOption.READ,
                    StandardOpenOption.WRITE,
                    StandardOpenOption.CREATE_NEW,
                ).use { fileChannel ->
                    block(tempFile, fileChannel)
                }
            } // temp file was moved inside the block to target file location - there is no need to delete it.
    } catch (t: Throwable) {
        tempFile.deleteIfExistsWithLogging("Exception occurred, temp file was deleted", t)
        throw t
    }
}

fun Path.deleteIfExistsWithLogging(onSuccessMessage: String, originalThrowable: Throwable? = null) {
    try {
        deleteIfExists()
        logger.trace("### ${System.currentTimeMillis()} $name: $onSuccessMessage")
    } catch (t: Throwable) {
        val suffix = originalThrowable?.let { "(After ${it::class.simpleName})" } ?: ""
        logger.debug("### ${System.currentTimeMillis()} $name: failed to delete file $suffix", t)
    }
}

/**
 * Acquires an exclusive lock on this channel's file, retrying in case of "Resource deadlock avoided" exception.
 * See the "Why retry?" section below for more details.
 *
 * This function blocks until the file can be locked or the current coroutine is canceled, whichever comes first.
 * If a "resource deadlock avoided" exception is thrown, this function suspends and retries several times.
 *
 * File locks are held on behalf of the entire Java virtual machine. They are not suitable for controlling access to a
 * file by multiple threads within the same virtual machine.
 *
 * ### Why retry?
 *
 * The "Resource deadlock avoided" exception is an error coming from the OS itself when it thinks it detects deadlocks.
 * The problem is that this check is at the level of processes and doesn't know about threads. So, if 2 processes both
 * lock the same 2 files at the same time, but each in different threads, the system will think there is a deadlock even
 * when there isn't:
 * * Process 1, thread A, gets lock on file A.
 * * Process 2, thread B, gets lock on file B.
 * * Process 1, thread B, tries to lock file B and blocks.
 * * Process 2, thread A, tries to lock file A and fails with "Resource deadlock avoided" exception.
 *
 * Since we can't really prevent this, our best bet is just to retry a few times to see if the locks are eventually
 * released. If not, we can finally rethrow the exception.
 *
 * @throws ClosedChannelException If this channel is closed
 * @throws AsynchronousCloseException If another thread closes this channel while the invoking thread is blocked in
 *         this function
 * @throws OverlappingFileLockException If a lock that overlaps the requested region is already held by this Java
 *         virtual machine, or if another thread is already blocked in this function and is attempting to lock an
 *         overlapping region of the same file
 * @throws NonWritableChannelException If this channel was not opened for writing
 * @throws IOException If some other I/O error occurs
 */
private suspend fun FileChannel.lockWithRetry(): FileLock? =
    withRetry(retryOnException = { it is IOException && it.message?.contains("Resource deadlock avoided") == true }) {
        runInterruptible {
            lock()
        }
    }

// todo (AB): 10 seconds is not enough
suspend fun <T> withRetry(
    retryCount: Int = 50,
    retryInterval: Duration = 200.milliseconds,
    retryOnException: (e: Exception) -> Boolean = { true },
    block: suspend (attempt: Int) -> T,
): T {
    contract {
        callsInPlace(block, InvocationKind.AT_LEAST_ONCE)
    }
    var attempt = 0
    var firstException: Exception? = null
    do {
        if (attempt > 0) delay(retryInterval)
        try {
            return block(attempt)
        } catch (e: Exception) {
            coroutineContext.ensureActive()
            if (!retryOnException(e)) {
                throw e
            }
            if (firstException == null) {
                firstException = e
            } else {
                firstException.addSuppressed(e)
            }
        }
        attempt++
    } while (attempt < retryCount)

    throw firstException
}

private fun tempLockFile(tmpDir: Path, targetFileName: String) = tmpDir.resolve("~${targetFileName}.amper.lock")
