package org.example.impl

import org.example.api.Model

fun parseDependencies(decoded: Map<String, Any>): MutableMap<String, List<String>> {
    @Suppress("UNCHECKED_CAST")
    val dependenciesRaw = decoded["dependencies"] as? Map<String, *>

    fun Map<String, *>.getPlainDependencies() = entries
        .filter { it.value is String }
        .map {
            if (it.value == "local") "[local]${it.key}"
            else "${it.key}:${it.value}"
        }

    val defaultDependencies = dependenciesRaw
        ?.getPlainDependencies()
        ?: emptyList()

    // Targets.
    @Suppress("UNCHECKED_CAST")
    val targetsRaw = decoded["target"] as? Map<String, *>

    @Suppress("UNCHECKED_CAST")
    val targetDependencies = targetsRaw
        ?.entries
        ?.filter { it.value is Map<*, *> }
        ?.map { it.key to it.value as Map<String, *> }
        ?.filter { it.second["dependencies"] is Map<*, *> }
        ?.associate { it.first to (it.second["dependencies"] as Map<String, *>).getPlainDependencies() }
        ?: emptyMap()

    val dependencies = mutableMapOf<String, List<String>>().apply {
        put(Model.defaultTarget, defaultDependencies)
        putAll(targetDependencies)
    }
    return dependencies
}