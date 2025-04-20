/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.concurrency

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

// initially from intellij:community/platform/build-scripts/downloader/src/striped.kt

/**
 * A mutex that ensures exclusivity within groups of callers (or "stripes").
 * It contains [stripeCount] mutexes, and each locking action requires some hash value to identify which stripe to lock.
 *
 * **The given [stripeCount] must be a power of 2.**
 * This is because the mutexes are addressed using bitwise operations for performance.
 *
 * This kind of locking is useful when we want to avoid a global lock and instead protect only individual resources,
 * but the number of resources is too large (or unknown) to create all mutexes up front (or at all).
 * We could create mutexes dynamically in a concurrent map, but then we would pay the synchronization costs on the map
 * lookup in addition to the mutex itself. We would also have no way to know when to clear the map, so it would also
 * eventually blow the memory.
 */
class StripedMutex(stripeCount: Int = 64) {
    private val mask = stripeCount - 1
    private val mutexes = Array(stripeCount) { Mutex() }

    init {
        require(stripeCount > 0) { "stripeCount must be positive, got $stripeCount" }
        require(stripeCount and (stripeCount - 1) == 0) { "stripeCount must be a power of 2, got $stripeCount" }
    }

    /**
     * Gets the [Mutex] corresponding to the given [hash].
     *
     * Invocations with the same hash are guaranteed to return the same [Mutex].
     * Invocations with different hashes may or may not return the same [Mutex]
     * (hashes in the same "stripe" share the same mutex).
     *
     * @see StripedMutex.withLock
     */
    fun getMutex(hash: Int): Mutex = mutexes[hash and mask]
}

/**
 * Runs the given [action] under the lock identified by the given [hash].
 *
 * Invocations with the same hash are guaranteed to use the same underlying [Mutex].
 * Invocations with different hashes may or may not use the same [Mutex]
 * (hashes in the same "stripe" share the same mutex).
 *
 * When [owner] is specified (non-null value) and this mutex is already locked with the same owner (same identity),
 * this function throws IllegalStateException (useful for debugging unexpected re-entries).
 */
suspend inline fun <T> StripedMutex.withLock(hash: Int, owner: Any? = null, action: () -> T) =
    getMutex(hash).withLock(owner, action)