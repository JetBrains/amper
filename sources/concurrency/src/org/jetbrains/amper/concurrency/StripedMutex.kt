/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.concurrency

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

// initially from intellij:community/platform/build-scripts/downloader/src/striped.kt

private const val MAX_POWER_OF_TWO = 1 shl Integer.SIZE - 2
private const val ALL_SET = 0.inv()

/**
 * A mutex that ensures exclusivity only within groups of callers (or "stripes").
 * It contains multiple mutexes, and each locking action requires some hash value to identify which stripe to lock.
 *
 * This is useful when we don't want a global lock, but rather a lock associated to a specific resource that we can
 * identify with a hash. Using one mutex per resource would be a waste, so using stripe is a compromise.
 */
class StripedMutex(stripeCount: Int = 64) {
    private val mask = if (stripeCount > MAX_POWER_OF_TWO) ALL_SET else (1 shl (Integer.SIZE - Integer.numberOfLeadingZeros(stripeCount - 1))) - 1
    private val mutexes = Array(mask + 1) { Mutex() }

    fun getMutex(hash: Int): Mutex = mutexes[hash and mask]
}

/**
 * Runs the given [action] under the lock identified by the given [hash].
 * Invocations with the same hash are guaranteed to use the same underlying [Mutex].
 *
 * When [owner] is specified (non-null value) and this mutex is already locked with the same owner (same identity),
 * this function throws IllegalStateException (useful for debugging unexpected re-entries).
 */
suspend inline fun <T> StripedMutex.withLock(hash: Int, owner: Any? = null, action: () -> T) =
    getMutex(hash).withLock(owner, action)
