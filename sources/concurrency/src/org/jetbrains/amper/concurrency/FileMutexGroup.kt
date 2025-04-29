/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.concurrency

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.nio.file.Path
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

/**
 * A group of [Mutex]es associated with files.
 *
 * This is useful in the context of double-locking, to acquire a JVM level mutex associated to a lock file before
 * acquiring a process level OS lock on the file.
 */
interface FileMutexGroup {

    /**
     * Gets the [Mutex] corresponding to the given [path].
     *
     * * Invocations with the same [path] must return the same [Mutex].
     * * Invocations with different paths that point to the same file must return the same [Mutex].
     * * Invocations with paths pointing to different files generally shouldn't return the same [Mutex], but they may.
     *   In short, some mutexes can be shared between multiple files.
     */
    fun getMutex(path: Path): Mutex

    companion object {

        /**
         * A default [FileMutexGroup], backed by a [StripedMutex]. See [striped] for details.
         */
        val Default: FileMutexGroup = striped(stripeCount = 512)

        /**
         * Creates a new [FileMutexGroup] backed by a [StripedMutex] with the given [stripeCount].
         *
         * This type of [FileMutexGroup] reuses the same mutex for multiple files, which means that it protects "too
         * much", but it is still more granular than a global lock.
         *
         * It is useful because the number of paths is potentially infinite, so we cannot possibly create all mutexes
         * up front for each possible path. If we instead created a synchronized map of Paths -> Mutexes, we would have
         * to synchronize on every lookup, which could be too expensive. It would also require some kind of synchronized
         * cleanup to avoid blowing up the memory.
         */
        fun striped(stripeCount: Int): FileMutexGroup = StripedFileMutexGroup(stripeCount)
    }
}

private class StripedFileMutexGroup(stripeCount: Int) : FileMutexGroup {
    private val stripedMutex = StripedMutex(stripeCount = stripeCount)

    override fun getMutex(path: Path): Mutex {
        // we use the absolute path to make sure we consider all "equivalent" paths the same
        val hash = path.toAbsolutePath().normalize().hashCode()
        return stripedMutex.getMutex(hash)
    }
}

/**
 * Runs the given [block] under the lock identified by the given [path].
 *
 * * Invocations with the same [path] must use the same [Mutex].
 * * Invocations with different paths that point to the same file must use the same [Mutex].
 * * Invocations with paths pointing to different files generally shouldn't use the same [Mutex], but they may.
 *   In short, some mutexes can be shared between multiple files.
 *
 * When the given [owner] is non-null and the mutex is already locked with the same owner (same identity),
 * this function throws [IllegalStateException] (useful for debugging unexpected re-entries).
 */
suspend inline fun <T> FileMutexGroup.withLock(path: Path, owner: Any? = null, block: () -> T): T {
    contract {
        callsInPlace(block, InvocationKind.EXACTLY_ONCE)
    }
    return getMutex(path).withLock(owner, block)
}
