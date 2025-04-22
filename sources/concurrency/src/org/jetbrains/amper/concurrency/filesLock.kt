/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.concurrency

import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import org.slf4j.LoggerFactory
import java.io.IOException
import java.nio.channels.ClosedChannelException
import java.nio.channels.FileChannel
import java.nio.channels.FileLock
import java.nio.file.NoSuchFileException
import java.nio.file.OpenOption
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.util.*
import kotlin.coroutines.coroutineContext
import kotlin.io.path.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.deleteIfExists
import kotlin.io.path.exists
import kotlin.io.path.moveTo
import kotlin.io.path.name
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.math.min
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

private val logger = LoggerFactory.getLogger("fileLock.kt")

private val filesLock = StripedMutex(stripeCount = 512)

/**
 * Executes the given [block] under a double lock:
 * * a non-reentrant coroutine Mutex based on the [lockFile]'s path, getting exclusive access inside the current JVM
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
suspend fun <T> withDoubleLock(
    lockFile: Path,
    owner: Any? = null,
    options: Array<out OpenOption> = arrayOf(
        StandardOpenOption.READ,
        StandardOpenOption.WRITE,
        StandardOpenOption.CREATE,
    ),
    block: suspend (lockFileChannel: FileChannel) -> T
): T {
    // The first lock locks the stuff inside one JVM process
    val localMutexKey = lockFile.toAbsolutePath().normalize().hashCode()
    return filesLock.withLock(localMutexKey, owner) {
        // The second lock locks a flagFile across all processes on the system
        lockFile.withFileChannelLock(*options) {
            block(it)
        }
    }
}

private suspend inline fun <T> Path.withFileChannelLock(vararg options: OpenOption, block: (FileChannel) -> T): T {
    // Files can sometimes be inaccessible for a short time right after a removal.
    val lockFileChannel = withRetryOnAccessDenied {
        FileChannel.open(this, *options)
    }
    return lockFileChannel.use { fileChannel ->
        fileChannel.lockWithRetry().use {
            block(fileChannel)
        }
    }
}

/**
 * Create a target file once and reuse it as a result of further invocations until its hash is valid.
 *
 * Method could be used safely from different JVM threads and from different processes.
 *
 * To achieve such concurrency safety, it does the following:
 * - creates a temporary lock file inside the temp directory (resolved with the help of given lambda [tempDir]),
 * - takes JVM and inter-process locks on that file
 * - creates a temporary file inside temp directory
 * - under the lock write content to that file with help of the given lambda ([writeFileContent])
 * - after the file was successfully written to temp location, its sha1 hash is stored into the target file directory
 * - finally, temporary file is moved to the target file location.
 *
 * The following two restrictions are applied on the input parameters for correct locking logic:
 *  - __MUST__ the same temporary directory is used for the given target file no matter
 *      how many times the method is called;
 *  - __SHOULD__ different target files with the same file names correspond to different temp directories
 *      (if this is not met, some contention might be observed during downloading of such files)
 *
 * Note: Since the first lock is a non-reentrant coroutine [kotlinx.coroutines.sync.Mutex],
 *  callers MUST not call locking methods defined in this utility file again from inside the lambda
 *  ([writeFileContent]) - that would lead to the hanging coroutine.
 */
suspend fun produceFileWithDoubleLockAndHash(
    target: Path,
    tempDir: suspend () -> Path = {
        // todo (AB) : Add path checksum to avoid contention if different files have the same name but different paths.
        Path(System.getProperty("java.io.tmpdir")).resolve(".amper")
    },
    writeFileContent: suspend (Path, FileChannel) -> Boolean
): Path? {
    return produceResultWithDoubleLock(
        tempDir(),
        target.name,
        getAlreadyProducedResult = {
            // todo (AB) : replace with logic from ExecuteOnChange (hashes are too heavy)
            if (!target.exists()) {
                null
            } else {
                val hashFile = target.parent.resolve("${target.name}.sha1")
                if (!hashFile.exists()) {
                    null
                } else {
                    val expectedHash = hashFile.readText()
                    val actualHash = computeHash(target, "sha1").hash

                    if (expectedHash != actualHash) {
                        null
                    } else {
                        target
                    }
                }
            }
        }
    ) { tempFilePath, fileChannel ->
        val isSuccessfullyWritten = writeFileContent(tempFilePath, fileChannel)
            .also {
                if (it) {
                    val hashFile = target.parent.resolve("${target.name}.sha1")
                    // Store sha1 of resulted sourceSet file
                    val hasher = Hasher("sha1")
                    fileChannel.position(0)
                    fileChannel.readTo(listOf(hasher.writer))
                    hashFile.parent.createDirectories()
                    hashFile.writeText(hasher.hash)
                }
            }

        if (isSuccessfullyWritten) {
            target.parent.createDirectories()
            tempFilePath.moveTo(target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        }

        target.takeIf { isSuccessfullyWritten }
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
    return (fileLockSource ?: filesLock).withLock(tempLockFile.hashCode()) {
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

private suspend fun FileChannel.lockWithRetry(): FileLock? =
    withRetry(
        retryOnException = { e ->
            e is IOException && e.message?.contains("Resource deadlock avoided") == true
        }
    ) {
        lock()
    }

// todo (AB): 10 seconds is not enough
suspend fun <T> withRetry(
    retryCount: Int = 50,
    retryInterval: Duration = 200.milliseconds,
    retryOnException: (e: Exception) -> Boolean = { true },
    block: suspend (attempt: Int) -> T,
): T {
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
