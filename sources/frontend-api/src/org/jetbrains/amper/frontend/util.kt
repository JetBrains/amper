/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend

import java.util.*
import kotlin.collections.AbstractMap
import kotlin.reflect.KMutableProperty0

fun String.doCapitalize() = replaceFirstChar { it.titlecase() }

val AmperModule.mavenRepositories: List<RepositoriesModulePart.Repository>
    get() = parts.find<RepositoriesModulePart>()?.mavenRepositories ?: emptyList()

fun Fragment.allFragmentDependencies(includeSelf: Boolean = false): Sequence<Fragment> = sequence {
    if (includeSelf) {
        yield(this@allFragmentDependencies)
    }
    yieldAll(allFragmentDependencies(null).map { it.target })
}

/**
 * Simple approach to traverse dependencies transitively.
 *
 * TODO: maybe generalize [ancestralPath] to use instead of this?
 */
private fun Fragment.allFragmentDependencies(
    traverseTypes: Set<FragmentDependencyType>? = null,
): Sequence<FragmentLink> = sequence {
    val traversed = hashSetOf<FragmentLink>()
    val stack = ArrayList(fragmentDependencies)
    while (stack.isNotEmpty()) {
        val link = stack.removeLast()
        if (traverseTypes != null && link.type !in traverseTypes) continue
        if (traversed.add(link)) {
            yield(link)
            stack.addAll(link.target.fragmentDependencies)
        }
    }
}

/**
 * Returns the path from this [Fragment] to its farthest ancestor (more general parents). *
 * Closer parents appear first, even when they're on different paths.
 *
 * Example:
 * ```
 *      common
 *      /    \
 *  desktop  apple
 *      \    /
 *     macosX64
 * ```
 * In this situation calling [ancestralPath] on `macosX64` yields `[macosX64, desktop, apple, common]`.
 * The order between `desktop` and `apple` is unspecified.
 */
fun Fragment.ancestralPath(): Sequence<Fragment> = sequence {
    val seenAncestorNames = mutableSetOf<String>()

    val queue = ArrayDeque<Fragment>()
    queue.add(this@ancestralPath)
    while (queue.isNotEmpty()) {
        val fragment = queue.removeFirst()
        yield(fragment)
        fragment.fragmentDependencies.forEach { link ->
            val parent = link.target
            if (parent.name !in seenAncestorNames) {
                queue.add(parent)
                seenAncestorNames.add(parent.name)
            }
        }
    }
}

/**
 * Simple class to associate enum values by some string key.
 */
abstract class EnumMap<EnumT : Enum<EnumT>, KeyT>(
    valuesGetter: () -> Array<EnumT>,
    private val key: EnumT.() -> KeyT,
) : AbstractMap<KeyT, EnumT>() {
    override val entries = valuesGetter().associateBy { it.key() }.entries.toMutableSet()
}

/**
 * A class that every enum, participating in
 * schema building, should inherit.
 */
interface SchemaEnum {
    val schemaValue: String
    val outdated: Boolean
}

interface Context {
    val parent: Context?
    val isLeaf: Boolean
    val pretty: String
    val leaves: Set<Context>
}