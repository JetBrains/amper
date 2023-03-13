package org.jetbrains.deft.proto.frontend.fragments

import org.jetbrains.deft.proto.frontend.Model

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