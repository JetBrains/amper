/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.dependency.resolution

import java.util.concurrent.ConcurrentHashMap
import kotlin.reflect.KClass

inline fun <reified T : Any> Key(name: String): Key<T> = Key(name, T::class)

data class Key<T : Any>(val name: String, private val kClass: KClass<T>)

/**
 * Heterogeneous map that allows type-safe access by using [Key]s to retrieve/put values.
 */
@Suppress("UNCHECKED_CAST")
open class TypedKeyMap(protected val map: MutableMap<Key<*>, Any> = ConcurrentHashMap()) {
    operator fun <T : Any> get(key: Key<T>): T? = map[key] as T?

    operator fun <T : Any> set(key: Key<T>, value: T) {
        map[key] = value
    }

    fun <T : Any> computeIfAbsent(key: Key<T>, mappingFunction: (Key<T>) -> T): T =
        map.computeIfAbsent(key, mappingFunction as (Key<*>) -> Any) as T
}
