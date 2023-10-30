/*
 * Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend

import org.jetbrains.amper.core.messages.BuildProblem
import org.jetbrains.amper.core.messages.Level
import org.jetbrains.amper.core.messages.ProblemReporter
import org.jetbrains.amper.frontend.nodes.YamlNode
import org.jetbrains.amper.frontend.util.*
import java.nio.file.Path

typealias TypesafeVariants = List<Variant>

interface BuildFileAware {
    val buildFile: Path

    fun ProblemReporter.reportWithinFile(message: String) =
        reportMessage(BuildProblem(message = message, file = buildFile, level = Level.Error))

    fun ProblemReporter.reportWithinNode(node: YamlNode, message: String) =
        reportMessage(
            BuildProblem(
                message = message,
                file = buildFile,
                level = Level.Error,
                line = node.startMark.line,
            )
        )
}

interface Stateful<K, V> {
    val state: MutableMap<K, V>
}

fun <T> cartesianSets(elements: Iterable<Collection<T>>): List<Set<T>> =
    elements.cartesianGeneric(
        { emptySet() },
        { this.toSet() },
        Set<T>::plus,
        preserveLowerDimensions = false,
        preserveEmpty = false,
    )

context (ParsingContext)
fun Set<Platform>.toCamelCaseString(): Pair<String, String?> {
    val platforms = this.toMutableSet()
    val aliases: List<String> = buildList {
        aliasMap.entries
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

context (Stateful<FragmentBuilder, Fragment>, TypesafeVariants)
internal val List<FragmentBuilder>.immutableFragments: List<Fragment>
    get() = map {
        it.build()
    }

context (Stateful<FragmentBuilder, Fragment>, TypesafeVariants)
internal val List<FragmentBuilder>.immutableLeafFragments: List<LeafFragment>
    get() = map {
        it.buildLeaf()
    }

context (Stateful<FragmentBuilder, Fragment>, TypesafeVariants)
internal val List<ArtifactBuilder>.immutableArtifacts: List<Artifact>
    get() = map { it.build() }
