/*
 * Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.aomBuilder

import org.jetbrains.amper.frontend.ClassBasedSet
import org.jetbrains.amper.frontend.Fragment
import org.jetbrains.amper.frontend.FragmentPart
import org.jetbrains.amper.frontend.LeafFragment
import org.jetbrains.amper.frontend.Model
import org.jetbrains.amper.frontend.ModelImpl
import org.jetbrains.amper.frontend.PotatoModule
import org.jetbrains.amper.frontend.plus
import org.jetbrains.amper.frontend.processing.merge
import org.jetbrains.amper.frontend.schema.Settings
import org.jetbrains.amper.frontend.toClassBasedSet

// Copy paste from "sources/frontend-api/src/org/jetbrains/amper/frontend/resolve/resolve.kt"
// TODO Need to be removed after parts replacement with Settings.

val Model.resolved: Model
    get() = ModelImpl(
        this@resolved.modules.map { it.withResolvedParts }
    )

val PotatoModule.withResolvedParts: PotatoModule get() = object : PotatoModule by this {
    override val fragments: List<Fragment> =
        this@withResolvedParts.fragments.resolve(this@withResolvedParts)
}

fun List<Fragment>.resolve(module: PotatoModule): List<Fragment> = buildList {
    var root: Fragment? = this@resolve.firstOrNull()
    while (root?.fragmentDependencies?.isNotEmpty() == true) {
        root = root.fragmentDependencies.firstOrNull()?.target
    }
    val deque = ArrayDeque<Fragment>()
    val alreadyResolved = mutableSetOf<String>()
    root?.let {
        val resolvedFragment = it.resolve(module)
        add(resolvedFragment)
        deque.add(resolvedFragment)
        alreadyResolved.add(it.name)
    }

    while (deque.isNotEmpty()) {
        val fragment = deque.removeFirst()
        fragment.fragmentDependants.forEach { link ->
            val dependant = link.target
            // Equals comparison works not good for anonymous objects.
            // Also, fragment names are unique, so we can use it.
            if (dependant.name !in alreadyResolved) {
                val resolved = dependant.resolve(module, fragment)
                alreadyResolved.add(resolved.name)
                deque.add(resolved)
                add(resolved)
            }
        }
    }
}


fun Fragment.resolve(module: PotatoModule, parent: Fragment? = null): Fragment {
    return resolveParts(parts + parent?.parts.orEmpty().toClassBasedSet(), module, parent)
}

@Suppress("UNCHECKED_CAST")
private fun Fragment.resolveParts(
    parts: ClassBasedSet<FragmentPart<*>>,
    module: PotatoModule,
    parent: Fragment?,
): Fragment {
    val resolvedParts = parts.map {
        propagateFor(it as FragmentPart<Any>).default(module)
    }.toClassBasedSet()
    val mergedSettings: Settings = parent?.settings?.merge(settings) ?: settings
    return createResolvedAdapter(resolvedParts, mergedSettings)
}


fun Fragment.createResolvedAdapter(
    resolvedParts: ClassBasedSet<FragmentPart<*>>,
    mergedSettings: Settings,
) = if (this@createResolvedAdapter is LeafFragment)
    object : LeafFragment by this@createResolvedAdapter {
        @Deprecated("Should be replaced with [settings]", ReplaceWith("settings"))
        override val parts: ClassBasedSet<FragmentPart<*>> get() = resolvedParts
        override val settings = mergedSettings
        override fun equals(other: Any?) = other != null &&
                other is LeafFragment &&
                name == other.name
        override fun hashCode() = name.hashCode()
    }
else object : Fragment by this@createResolvedAdapter {
    @Deprecated("Should be replaced with [settings]")
    override val parts = resolvedParts
    override val settings = mergedSettings
    override fun equals(other: Any?) = other != null &&
            other is LeafFragment &&
            name == other.name

    override fun hashCode() = name.hashCode()
}

fun Fragment.propagateFor(
    parentPart: FragmentPart<Any>
): FragmentPart<*> {
    val sourcePart = parts[parentPart::class.java]
    return if (parentPart !== sourcePart) {
        sourcePart?.propagate(parentPart) ?: parentPart
    } else sourcePart
}
