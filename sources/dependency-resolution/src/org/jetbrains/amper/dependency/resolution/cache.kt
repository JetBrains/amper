/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

@file:Suppress("UNCHECKED_CAST")

package org.jetbrains.amper.dependency.resolution

import io.ktor.http.parameters
import org.slf4j.LoggerFactory
import java.io.Closeable
import java.net.http.HttpClient
import java.util.concurrent.ConcurrentHashMap
import kotlin.reflect.KClass

inline fun <reified T : Any> Key(name: String): Key<T> = Key(name, T::class)

data class Key<T : Any>(val name: String, private val kClass: KClass<T>)

class Cache(private val cache: MutableMap<Key<*>, Any> = ConcurrentHashMap()) : Closeable {
    private val logger = LoggerFactory.getLogger("cache.kt")

    operator fun <T : Any> get(key: Key<T>): T? = cache[key] as T?

    operator fun <T : Any> set(key: Key<T>, value: T): T? = put(key, value)

    fun <T : Any> put(key: Key<T>, value: T): T? = cache.put(key, value) as T?

    fun <T : Any> putIfAbsent(key: Key<T>, value: T): T? = cache.putIfAbsent(key, value) as T?

    fun <T : Any> computeIfAbsent(key: Key<T>, mappingFunction: (Key<T>) -> T): T =
        cache.computeIfAbsent(key, mappingFunction as (Key<*>) -> Any) as T

    override fun close() {
        val closeableEntries = cache.filterValues { it is AutoCloseable || it is HttpClient}
        closeableEntries.forEach { it ->
            val closeable = it.value
            cache.remove(it.key)
            try {
                // In java 21 HttpClient is AutoClosable,
                // but it has an issue that prevents it from shutting down https://bugs.openjdk.org/browse/JDK-8316580
                // Calling 'HttpClient.shutdownNow' is a work around
                (closeable as? HttpClient)
                    ?.let {
                        HttpClient::class.java.declaredMethods.firstOrNull { it.name == "shutdownNow" && it.parameterTypes.isEmpty() }
                            ?: HttpClient::class.java.declaredMethods.firstOrNull { it.name == "shutdown" && it.parameterTypes.isEmpty() }
                    }
                    ?.let {
                        logger.debug("Calling ${it.declaringClass.name}.${it.name} (on closing DR context)")
                        it.invoke(closeable)
                    }
                    ?: (closeable as? AutoCloseable)?.close()
            } catch (e: Exception) {
                logger.warn("Failed to close DR context resource", e)
            }
        }
    }
}
