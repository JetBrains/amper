/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.concurrency

import java.util.concurrent.ConcurrentHashMap

/**
 * A map that guarantees safe concurrent read/writes without global lock on the whole map, and that is also
 * coroutines-friendly.
 *
 * This implementation provides a [computeIfAbsent] function that guarantees a single execution like
 * [ConcurrentMap.computeIfAbsent][java.util.concurrent.ConcurrentMap.computeIfAbsent] but also supports suspend
 * functions to calculate the value like Kotlin's [ConcurrentMap.getOrPut][java.util.concurrent.ConcurrentMap.getOrPut].
 *
 * To allow this, the map is guarded with a [StripedMutex] that splits the map into [stripeCount] stripes, each holding
 * its own mutex to read and write the keys that fall in that stripe.
 */
class AsyncConcurrentMap<K : Any, V : Any>(stripeCount: Int = 64) {

    private val map = ConcurrentHashMap<K, V>()

    private val stripedMutex = StripedMutex(stripeCount = stripeCount)

    /**
     * Concurrent [computeIfAbsent], that is safe for concurrent maps.
     *
     * Returns the value for the given [key]. If the key is not found in the map, calls the [computeValue] function,
     * puts its result into the map under the given key, and returns it.
     *
     * This method guarantees not to put the value into the map if the key is already there,
     * and it also guarantees that the [computeValue] function is invoked at most once for each key.
     */
    suspend fun computeIfAbsent(key: K, computeValue: suspend () -> V): V =
        // ConcurrentMap.getOrPut guarantees atomic insert but doesn't guarantee that computeValue() will only be
        // called once, so without locking we could call it twice. The JDK's computeIfAbsent() would solve this problem
        // but doesn't support suspend functions.
        stripedMutex.withLock(key.hashCode()) {
            map.getOrPut(key) { computeValue() }
        }
}
