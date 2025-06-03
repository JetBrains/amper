/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

@file:Suppress("UNCHECKED_CAST")

package org.jetbrains.amper.dependency.resolution

import org.slf4j.LoggerFactory
import java.io.Closeable

class Cache : TypedKeyMap(), Closeable {
    private val logger = LoggerFactory.getLogger("cache.kt")

    override fun close() {
        val closeableEntries = map
            .minus(httpClientKey)                 // calling side handles lifecycle of injected HttpClient
            .filterValues { it is AutoCloseable }

        closeableEntries.forEach { it ->
            val closeable = it.value as AutoCloseable
            map.remove(it.key)
            try {
                closeable.close()
            } catch (e: Exception) {
                logger.warn("Failed to close DR context resource", e)
            }
        }
    }
}
