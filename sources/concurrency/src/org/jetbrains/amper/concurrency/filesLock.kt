package org.jetbrains.amper.concurrency

import kotlinx.coroutines.sync.withLock
import java.nio.channels.FileChannel
import java.nio.file.OpenOption
import java.nio.file.Path
import java.nio.file.StandardOpenOption

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

suspend fun Path.holdsLock(owner: Any) : Boolean = holdsLock(this.hashCode(), owner)
suspend fun holdsLock(hash: Int, owner: Any) : Boolean = filesLock.getLock(hash).holdsLock(owner)




