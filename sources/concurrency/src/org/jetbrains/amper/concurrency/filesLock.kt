package org.jetbrains.amper.concurrency

import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.withLock
import java.io.IOException
import java.nio.channels.ClosedChannelException
import java.nio.channels.FileChannel
import java.nio.channels.FileLock
import java.nio.channels.OverlappingFileLockException
import java.nio.file.NoSuchFileException
import java.nio.file.OpenOption
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import kotlin.coroutines.cancellation.CancellationException
import kotlin.io.path.createDirectories
import kotlin.io.path.deleteIfExists
import kotlin.io.path.exists
import kotlin.io.path.moveTo
import kotlin.io.path.name
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */


private val filesLock = StripedMutex(stripeCount = 512)

/**
 * It locks on a non-reentrant coroutine Mutex first - getting exclusive access inside one JVM.
 * Then it acquires FileChannel lock to get exclusive access across all processes on the system.
 * And then, holding those two locks, it executes the given block.
 *
 * Both locks are unlocked after method return.
 *
 * Note: Since the first lock is a non-reentrant coroutine Mutex,
 * callers MUST not call withDoubleLock again from inside the given block - that would lead to the hanging coroutine.
 */
suspend fun <T> withDoubleLock(
    hash: Int,
    file: Path,
    owner: Any? = null,
    options: Array<out OpenOption> = arrayOf(StandardOpenOption.READ, StandardOpenOption.WRITE, StandardOpenOption.CREATE),
    block: suspend (FileChannel) -> T
) : T {
    // First lock locks the stuff inside one JVM process
    return withLock(hash, owner) {
        // Second, lock locks a flagFile across all processes on the system
        FileChannel.open(file, *options)
            .use { fileChannel ->
                fileChannel.lock().use {
                    block(fileChannel)
                }
            }
    }
}

suspend fun <T> Path.withLock(owner: Any? = null, block: suspend () -> T) : T = withLock(this.hashCode(), owner, block)
suspend fun <T> withLock(hash: Int, owner: Any? = null, block: suspend () -> T) : T =
    filesLock.getLock(hash).withLock(owner) {
        block()
    }

@Deprecated("Too low-level")
suspend fun Path.holdsLock(owner: Any) : Boolean = holdsLock(this.hashCode(), owner)
@Deprecated("Too low-level")
suspend fun holdsLock(hash: Int, owner: Any) : Boolean = filesLock.getLock(hash).holdsLock(owner)


/**
 * Create a target file once and reuse it as a result of further invocations until its hash is valid.
 *
 * Method could be used safely from different JVM threads and from different processes.
 *
 * To achieve such concurrency safety, it does the following:
 * - creates a temporary file with the help of given lambda <code>tempFilePath<code>,
 * - takes JVM and inter-process locks on that file
 * - under the locks write content to the file with help of the given lambda (writeFileContent)
 * - after the file was successfully written to temp location, its sha1 hash is stored into the target file directory
 * - finally, temporary file is moved to the target file location.
 *
 * The following two restrictions are applied on the input parameters for correct locking logic:
 *  - different target files correspond to different temp files
 *  - the same temporary file is used for the given target file no matter how many times the method is called.
 *
 * Note: Since the first lock is a non-reentrant coroutine Mutex,
 *  callers MUST not call locking methods defined in this utility file again from inside the lambda (writeFileContent) -
 *  that would lead to the hanging coroutine.
 */
suspend fun produceFileWithDoubleLockAndHash(
    target: Path,
    tempFilePath: suspend () -> Path = {
        // todo (AB) : Add path checksum to avoid contention if different files have the same name but different paths.
        Paths.get(System.getProperty("java.io.tmpdir")).resolve(".amper").resolve("~${target.name}")
    },
    returnAlreadyProducedWithoutLocking: Boolean = true,
    writeFileContent: suspend (FileChannel) -> Boolean
) : Path {
    return produceFileWithDoubleLock(
        target,
        tempFilePath,
        returnAlreadyProducedWithoutLocking,
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
                    val actualHash = computeHash(target, listOf(Hasher("sha1"))).single().hash

                    if (expectedHash != actualHash) {
                        null
                    } else {
                        target
                    }
                }
            }
        }
    ) { fileChannel ->
        writeFileContent(fileChannel)
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
    }
}

private suspend fun produceFileWithDoubleLock(
    target: Path,
    tempFilePath: suspend () -> Path,
    returnAlreadyProducedWithoutLocking: Boolean = true,
    getAlreadyProducedResult: suspend () -> Path? = { target.takeIf { it.exists() } },
    writeFileContent: suspend (FileChannel) -> Boolean,
) : Path {
    val temp = tempFilePath()

    return produceResultWithDoubleLock(
        target.hashCode(), temp,
        block = {
            if (writeFileContent(it)) {
                target.parent.createDirectories()
                temp.moveTo(target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            }
            target
        },
        getAlreadyProducedResult = getAlreadyProducedResult,
        returnAlreadyProducedWithoutLocking = returnAlreadyProducedWithoutLocking
    )
}

/**
 * It locks on a non-reentrant coroutine Mutex first - getting exclusive access inside one JVM.
 * Then it acquires FileChannel lock to get exclusive access across all processes on the system.
 * And then, holding those two locks, it executes the given block.
 *
 * Both locks are unlocked after method return.
 *
 * Note: Since the first lock is a non-reentrant coroutine Mutex,
 *  callers MUST not call locking methods defined in this file again from inside the given block -
 *  that would lead to the hanging coroutine.
 */
private suspend fun <T> produceResultWithDoubleLock(
    hash: Int,
    file: Path,
    block: suspend (FileChannel) -> T,
    returnAlreadyProducedWithoutLocking: Boolean = true,
    getAlreadyProducedResult: suspend () -> T?
) : T {
    // return already produced file without locking if allowed, otherwise proceed with file production
    if (returnAlreadyProducedWithoutLocking) getAlreadyProducedResult()?.let { return it }

    // First lock locks the stuff inside one JVM process
    return withLock(hash) {
        // Second lock locks a flagFile across all processes on the system
        produceResultWithFileLock(file, block, getAlreadyProducedResult)
    }
}

private suspend fun <T> produceResultWithFileLock(
    file: Path,
    block: suspend (FileChannel) -> T,
    getAlreadyProducedFile: suspend () -> T?
) : T {
    getAlreadyProducedFile()?.let { return it }

    file.parent.createDirectories()

    // Open temporary file channel
    val fileChannel = FileChannel.open(
        file,
        StandardOpenOption.READ,
        StandardOpenOption.WRITE,
        StandardOpenOption.CREATE
    )

    fileChannel.use {
        var wait = 10L
        while (true) {
            return@produceResultWithFileLock try {
                doWithFileLock(fileChannel, getAlreadyProducedFile, block, file)
            } catch (e: OverlappingFileLockException) {
                // Another process is already processing the file. Let's wait for it and then check the result.
                delay(wait)
                wait = (wait * 2).coerceAtMost(1000)
                continue
            } catch (e: NoSuchFileException) {
                // Another process deleted temp file before we were able to get the lock on it => Try again.
                produceResultWithFileLock(file, block, getAlreadyProducedFile)
            } catch (e: ClosedChannelException) {
                // Another process deleted temp file before we were able to get the lock on it => Try again.
                produceResultWithFileLock(file, block, getAlreadyProducedFile)
            }
        }
    }
}

private suspend fun <T> doWithFileLock(
    fileChannel: FileChannel,
    getAlreadyProducedFile: suspend () -> T?,
    block: suspend (FileChannel) -> T,
    file: Path
): T {
    val fileLock = fileChannel.lockWithRetry()
    return fileLock.use {
        try {
            getAlreadyProducedFile() ?: block(fileChannel)
        } finally {
            file.deleteIfExists()
        }
    }
}

suspend fun FileChannel.lockWithRetry(): FileLock? =
    withRetry(
        retryOnException = { e ->
            e is IOException && e.message?.contains("Resource deadlock avoided") == true
        }
    ) {
        lock()
    }

// todo (AB): 10 seconds is not enough
private suspend fun <T> withRetry(
    retryCount: Int = 50,
    retryInterval: Duration = 200.milliseconds,
    retryOnException: (e: Exception) -> Boolean = { true },
    block: () -> T,
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

