package org.example.impl

import org.example.api.CollapsedMap

/**
 * Collapse tree to plain map by adding keys as prefixes:
 * ```
 * a -> b -> c
 *  \-> d -> e
 *       \-> f
 * ```
 * to
 * ```
 * a.b: c
 * a.d: e
 * a.d: f
 * ```
 */
@Suppress("UNCHECKED_CAST")
fun Map<String, *>.collapse(separator: String = ".", prefix: String = ""): CollapsedMap {
    val maps: List<Map<String, List<String>>> = entries.map {
        when (val entryValue = it.value) {
            is Map<*, *> -> (entryValue as Map<String, Any>)
                .collapse(separator = separator, prefix = it.key)
                .map { mapIt -> "${it.key}$separator${mapIt.key}" to mapIt.value }
                .toMap()
            is List<*> -> mapOf(it.key to entryValue.map { "$it" })
            else -> mapOf(it.key to listOf("$entryValue"))
        }
    }
    val resultMap = mutableMapOf<String, MutableList<String>>()
    maps.forEach { map ->
        map.forEach { entry ->
            resultMap.getOrPut(entry.key) { mutableListOf() }.addAll(entry.value)
        }
    }
    return resultMap
}

@Suppress("UNCHECKED_CAST")
fun parseCollapsed(decoded: Map<String, Any>, attributeName: String) = (decoded[attributeName] as? Map<String, *>)
    ?.collapse()
    ?: emptyMap()