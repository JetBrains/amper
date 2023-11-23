/*
 * Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

@file:Suppress("UNCHECKED_CAST")

package org.jetbrains.amper.dependency.resolution

import kotlin.reflect.KClass

inline fun <reified T : Any> Key(name: String): Key<T> = Key(name, T::class)

data class Key<T : Any>(val name: String, private val kClass: KClass<T>)

class ResolutionCache(private val cache: MutableMap<Key<*>, Any> = mutableMapOf()) {

    operator fun <T : Any> get(key: Key<T>): T? = cache[key] as T?

    operator fun <T : Any> set(key: Key<T>, value: T): T? = put(key, value)

    fun <T : Any> put(key: Key<T>, value: T): T? = cache.put(key, value) as T?

    fun <T : Any> putIfAbsent(key: Key<T>, value: T): T? = cache.putIfAbsent(key, value) as T?

    fun <T : Any> computeIfAbsent(key: Key<T>, mappingFunction: (Key<T>) -> T): T =
        cache.computeIfAbsent(key, mappingFunction as (Key<*>) -> Any) as T
}
