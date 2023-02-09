package org.example.impl

import org.example.api.Model

fun parseTargets(decoded: Map<String, Any>): List<String> {
    @Suppress("UNCHECKED_CAST")
    val targetsRaw = decoded["target"] as? Map<String, *>

    @Suppress("UNCHECKED_CAST")
    val targets = targetsRaw
        ?.entries
        ?.map { it.key }
        ?: emptyList()

    return targets + Model.defaultTarget
}