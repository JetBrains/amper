package org.jetbrains.deft.proto.frontend

/**
 * Set, which elements are considered unique by their class.
 */
typealias ClassBasedSet<T> = Set<ByClassWrapper<T>>

class ByClassWrapper<T : Any> private constructor(
    val clazz: Class<*>,
    val value: T,
) {
    constructor(value: T): this(value::class.java, value)
    override fun equals(other: Any?) =
        (other as? ByClassWrapper<*>)?.clazz == clazz

    override fun hashCode() =
        clazz.hashCode()
}