package org.jetbrains.amper.concurrency

import kotlinx.coroutines.delay
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
import kotlin.coroutines.cancellation.CancellationException
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

/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

private val logger = LoggerFactory.getLogger("fileLock.kt")

private val filesLock = StripedMutex(stripeCount = 512)

/**
 * Executes the given [block] under a double lock:
 * * a non-reentrant coroutine Mutex for the given [hash], getting exclusive access inside one JVM
 * * a FileChannel lock on the given [file], getting exclusive access across all processes on the system
 *
 * Both locks are unlocked after method return.
 *
 * The block is passed the [FileChannel] used to lock the given [file], if further inspection of the file is needed
 * while under the lock.
 *
 * Note: Since the first lock is a non-reentrant coroutine Mutex, callers MUST not call withDoubleLock again from
 * inside the given [block], that would make the current coroutine hang.
 */
suspend fun <T> withDoubleLock(
    hash: Int,
    file: Path,
    owner: Any? = null,
    options: Array<out OpenOption> = arrayOf(StandardOpenOption.READ, StandardOpenOption.WRITE, StandardOpenOption.CREATE),
    block: suspend (lockFileChannel: FileChannel) -> T
) : T {
    // First lock locks the stuff inside one JVM process
    return filesLock.withLock(hash, owner) {
        // Second, lock locks a flagFile across all processes on the system
        file.withFileChannelLock(*options) {
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
 * - creates a temporary lock file inside the temp directory (resolved with the help of given lambda <code>tempDir<code>),
 * - takes JVM and inter-process locks on that file
 * - creates a temporary file inside temp directory
 * - under the lock write content to that file with help of the given lambda (<code>writeFileContent<code>)
 * - after the file was successfully written to temp location, its sha1 hash is stored into the target file directory
 * - finally, temporary file is moved to the target file location.
 *
 * The following two restrictions are applied on the input parameters for correct locking logic:
 *  - [MUST] the same temporary directory is used for the given target file no matter how many times the method is called;
 *  - [SHOULD] different target files with the same file names correspond to different temp directories
 *             (if this is not met, some contention might be observed during downloading of such files)
 *
 * Note: Since the first lock is a non-reentrant coroutine Mutex,
 *  callers MUST not call locking methods defined in this utility file again from inside the lambda (writeFileContent) -
 *  that would lead to the hanging coroutine.
 */
suspend fun produceFileWithDoubleLockAndHash(
    target: Path,
    tempDir: suspend () -> Path = {
        // todo (AB) : Add path checksum to avoid contention if different files have the same name but different paths.
        Path(System.getProperty("java.io.tmpdir")).resolve(".amper")
    },
    writeFileContent: suspend (Path, FileChannel) -> Boolean
) : Path? {
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
                    val actualHash = computeHash(target,"sha1").hash

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
 * given function <code>getAlreadyProducedResult<code>
 *
 * Method could be used safely from different JVM threads and from different processes.
 *
 * To achieve such concurrency safety, it does the following:
 * - creates a temporary lock file inside the given temp directory (<code>tempDir<code>),
 * - takes JVM and inter-process locks on that file
 * - creates a temporary file inside temp directory
 * - under the lock write content to that file with help of the given lambda (<code>block<code>)
 * - finally, temporary lock file is removed
 * - (temporary file with content is removed as well, but only in case exception was thrown from <code>block<code>,
 *    otherwise it is a responsibility of given <code>block<code> to remove temp file)
 *
 * The following two restrictions are applied on the input parameters for correct locking logic:
 *  - [MUST] the same temporary directory is used for the given target file no matter how many times the method is called
 *           (in case target file is produced by given <code>block<code>);
 *  - [SHOULD] different target files with the same file names correspond to different temp directories
 *             (if this is not met, some contention might be observed during downloading of such files)
 *
 * Note: Since the first lock is a non-reentrant coroutine Mutex,
 *  callers MUST not call locking methods defined in this utility file again from inside the lambda (writeFileContent) -
 *  that would lead to the hanging coroutine.
 */
suspend fun <T> produceResultWithDoubleLock(
    tempDir: Path,
    targetFileName: String,
    getAlreadyProducedResult: suspend () -> T?,
    block: suspend (Path, FileChannel) -> T,
) : T {
    // return already produced file without locking if allowed, otherwise proceed with file production
    getAlreadyProducedResult()?.let { return it }

    val tempLockFile = tempLockFile(tempDir, targetFileName)

    // First lock locks the stuff inside one JVM process
    return filesLock.withLock(tempLockFile.hashCode()) {
        // Second lock locks a flagFile across all processes on the system
        produceResultWithFileLock(tempDir, targetFileName, block, getAlreadyProducedResult)
    }
}

private suspend fun <T> produceResultWithFileLock(
    tempDir: Path,
    targetFileName: String,
    block: suspend (Path, FileChannel) -> T,
    getAlreadyProducedResult: suspend () -> T?
) : T {
    getAlreadyProducedResult()?.let { return it }

    while (true) {
        return try {
            // Open temporary lock file channel
            doWithFileLock(tempDir, targetFileName, getAlreadyProducedResult, block)
        } catch (e: NoSuchFileException) {
            // Another process deleted temp file before we were able to get the lock on it => Try again.
            logger.debug("NoSuchFileException from doWithFileLock on {}", e.file, e)
            continue
        } catch (e: ClosedChannelException) {
            // Another process deleted temp file before we were able to get the lock on it => Try again.
            logger.debug("ClosedChannelException from doWithFileLock on {}", targetFileName, e)
            continue
        }
    }
}

private suspend fun <T> doWithFileLock(
    tempDir: Path,
    targetFileName: String,
    getAlreadyProducedResult: suspend () -> T?,
    block: suspend (Path, FileChannel) -> T,
): T {

    val tempLockFile = tempLockFile(tempDir, targetFileName)

    tempLockFile.parent.createDirectories()

    return tempLockFile.withFileChannelLock(StandardOpenOption.WRITE, StandardOpenOption.CREATE) {
        val tempFileNameSuffix = UUID.randomUUID().toString().let { it.substring(0, min(8, it.length))}
        val tempFile = tempDir.resolve("~${targetFileName}.$tempFileNameSuffix")

        logger.trace("### ${System.currentTimeMillis()} ${tempLockFile.name}: locked")
        try {
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
            tempFile.deleteIfExistsWithLogging("Exception occurred, temp file was deleted under lock", t)
            throw t
        } finally {
            tempLockFile.deleteIfExistsWithLogging("Exception occurred, temp lock file was deleted under lock")
        }
    }.also {
        logger.trace("### ${System.currentTimeMillis()} {}: unlocked", tempLockFile.name)
    }
}

private fun Path.deleteIfExistsWithLogging(onSuccessMessage: String, t: Throwable? = null) {
    try {
        deleteIfExists()
        logger.trace("### ${ System.currentTimeMillis() } $name: $onSuccessMessage")
    } catch (_t: Throwable) {
        logger.debug("### ${ System.currentTimeMillis() } $name: failed to delete temp file under lock${ t?.let { "(After ${it::class.simpleName})" } ?: "" }", _t)
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
    block: suspend () -> T,
): T {
    var attempt = 0
    var firstException: Exception? = null
    do {
        if (attempt > 0) delay(retryInterval)
        try {
            return block()
        } catch (e: Exception) {
            if (e is CancellationException) {
                throw e
            } else if (!retryOnException(e)) {
                throw e
            } else {
                if (firstException == null) {
                    firstException = e
                } else {
                    firstException.addSuppressed(e)
                }
            }
        }
        attempt++
    } while (attempt < retryCount)

    throw firstException!!
}

private fun tempLockFile(tmpDir: Path, targetFileName: String) = tmpDir.resolve("~${targetFileName}.amper.lock")
