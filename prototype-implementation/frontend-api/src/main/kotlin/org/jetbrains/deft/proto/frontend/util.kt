package org.jetbrains.deft.proto.frontend

import java.util.*

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

private val prettyRegex = "_.".toRegex()
fun String.doCamelCase() = this.lowercase().replace(prettyRegex) { it.value.removePrefix("_").uppercase() }
fun String.doCapitalize() = this.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }