package org.jetbrains.deft.proto.frontend

import java.util.*


private val prettyRegex = "_.".toRegex()
fun String.doCamelCase() = this.lowercase().replace(prettyRegex) { it.value.removePrefix("_").uppercase() }
fun String.doCapitalize() = this.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }

/**
 * Shortcut to find an instance of a class in some collection.
 */
inline fun <reified T : Any> Collection<Any>.findInstance() = filterIsInstance<T>().firstOrNull()

/**
 * Simple approach to traverse dependencies transitively.
 */
fun Fragment.traverseDependencies(block: (FragmentLink) -> Unit) {
    val traversed = mutableSetOf<FragmentLink>()
    val blockPlusCheck: (FragmentLink) -> Unit = {
        if (!traversed.add(it)) error("Cyclic dependency!")
        block(it)
    }
    doTraverseDependencies(blockPlusCheck)
}

private fun Fragment.doTraverseDependencies(block: (FragmentLink) -> Unit) {
    fragmentDependencies.forEach(block)
    fragmentDependencies.map { it.target.doTraverseDependencies(block) }
}