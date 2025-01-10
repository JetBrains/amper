/*
 * Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend


/**
 * Set wrapper, that uses elements classes as key.
 */
class ClassBasedSet<T : Any> : AbstractMutableSet<T>() {

    private val internalMap = mutableMapOf<Class<*>, T>()

    override val size get() = internalMap.size

    override fun iterator() = object : MutableIterator<T> {
        private val internalIterator = internalMap.iterator()
        override fun hasNext() = internalIterator.hasNext()
        override fun next() = internalIterator.next().value
        override fun remove() = internalIterator.remove()
    }

    override fun add(element: T) =
        (internalMap.put(element::class.java, element) !== element)

    operator fun <T2 : T> get(clazz: Class<T2>): T2? = internalMap[clazz]?.let { clazz.cast(it) }
    inline fun <reified T2 : T> find() = this[T2::class.java]
}

fun <T : Any> classBasedSet() = ClassBasedSet<T>()

fun <T : Any> Iterable<T>.toClassBasedSet() = toCollection(ClassBasedSet())

operator fun <T : Any> ClassBasedSet<T>.plus(other: ClassBasedSet<T>) =
    buildClassBasedSet {
        addAll(this@plus)
        addAll(other)
    }

inline fun <T : Any> buildClassBasedSet(builderAction: MutableSet<T>.() -> Unit): ClassBasedSet<T> {
    return ClassBasedSet<T>().apply {
        builderAction()
    }
}