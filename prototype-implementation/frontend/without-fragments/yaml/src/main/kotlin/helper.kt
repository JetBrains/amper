package org.jetbrains.deft.proto.frontend

import java.nio.file.Path

interface BuildFileAware {
    val buildFile: Path
}

interface Stateful<K, V> {
    val state: MutableMap<K, V>
        get() = mutableMapOf()
}

fun <T> cartesian(vararg lists: List<T>): List<List<T>> {
    var res = listOf<List<T>>()
    for (list in lists) {
        res = cartesian(res, list.map { listOf(it) })
    }

    return res
}

fun <T> cartesian(list1: List<List<T>>, list2: List<List<T>>): List<List<T>> = buildList {
    if (list1.isEmpty()) {
        return list2
    }
    for (t1 in list1) {
        for (t2 in list2) {
            add(t1 + t2)
        }
    }
}

fun Set<Platform>.toCamelCaseString(): String {
    val platforms = this
    return buildString {
        for (platform in platforms) {
            val words = platform.toString().split("_")
            append(buildString {
                append(words[0].lowercase())
                for (i in 1 until words.size) {
                    append(words[i].lowercase().replaceFirstChar { it.uppercase() })
                }
            })
        }
    }
}