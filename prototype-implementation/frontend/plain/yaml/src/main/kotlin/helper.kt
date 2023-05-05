package org.jetbrains.deft.proto.frontend
import org.jetbrains.deft.proto.frontend.util.*

import java.nio.file.Path

interface BuildFileAware {
    val buildFile: Path
}

interface Stateful<K, V> {
    val state: MutableMap<K, V>
        get() = mutableMapOf()
}

fun <T> cartesianSets(elements: Iterable<Collection<T>>): List<Set<T>> =
    elements.cartesianGeneric(
        { emptySet() },
        { this.toSet() },
        Set<T>::plus,
        preserveLowerDimensions = false,
        preserveEmpty = false,
    )

context (Map<String, Set<Platform>>)
fun Set<Platform>.toCamelCaseString(): Pair<String, String?> {
    val platforms = this.toMutableSet()
    val aliases: List<String> = buildList {
        entries
            .filter { it.value.isNotEmpty() }
            .filter { platforms.containsAll(it.value) }
            .sortedByDescending { it.value.size }
            .forEach {
                if (platforms.containsAll(it.value)) {
                    add(it.key)
                    platforms -= it.value
                }
            }
    }

    var alias: String? = null
    return buildString {
        for (index in aliases.indices) {
            alias = aliases[index]
            if (index == 0) {
                append(alias)
            } else {
                append(alias!!.replaceFirstChar { it.uppercase() })
            }
        }
        for (platform in platforms) {
            val words = platform.toString().split("_")
            append(buildString {
                if (alias != null) {
                    append(words[0].lowercase().replaceFirstChar { it.uppercase() })
                } else {
                    append(words[0].lowercase())
                }
                for (i in 1 until words.size) {
                    append(words[i].lowercase().replaceFirstChar { it.uppercase() })
                }
            })
        }
    } to alias
}

val Platform.children: List<Platform>
    get() {
        val thisPlatform = this
        return buildList {
            for (platform in enumValues<Platform>()) {
                if (platform.parent == thisPlatform) {
                    add(platform)
                }
            }
        }
    }

val Platform.leafChildren: List<Platform>
    get() {
        return buildList {
            val queue = ArrayDeque<Platform>()
            queue.add(this@leafChildren)
            while (queue.isNotEmpty()) {
                val platform = queue.removeFirst()
                queue.addAll(platform.children)
                if (platform.isLeaf) {
                    add(platform)
                }
            }
        }
    }


internal tailrec fun Platform.native(): Boolean {
    if (this == Platform.COMMON) {
        return false
    }

    if (this == Platform.NATIVE) {
        return true
    }

    return this.parent!!.native()
}


fun String.transformKey(): String {
    val modeParts = split("-")
    if (modeParts.size == 1) {
        return this
    }
    val otherVariantOptionParts = modeParts[1].split("@")
    if (otherVariantOptionParts.size == 1) {
        return "${modeParts[1]}@${modeParts[0]}"
    }
    return "${otherVariantOptionParts[0]}@${otherVariantOptionParts[1]}+${modeParts[0]}"
}

context (Stateful<MutableFragment, Fragment>)
internal val List<MutableFragment>.immutable: List<Fragment> get() = map { it.immutable() }