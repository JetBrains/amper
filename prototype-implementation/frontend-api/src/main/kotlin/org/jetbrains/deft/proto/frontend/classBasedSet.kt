package org.jetbrains.deft.proto.frontend

import kotlin.contracts.InvocationKind
import kotlin.contracts.contract


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

    operator fun <T2 : T> get(clazz: Class<T2>): T2? = internalMap.get(clazz) as? T2
    inline fun <reified T2 : T> find() = this[T2::class.java]
}

fun <T : Any> classBasedSet() = ClassBasedSet<T>()

fun <T : Any> Iterable<T>.toClassBasedSet() = toCollection(ClassBasedSet())

inline fun <T : Any> buildClassBasedSet(builderAction: MutableSet<T>.() -> Unit): ClassBasedSet<T> {
    return ClassBasedSet<T>().apply {
        builderAction()
    }
}