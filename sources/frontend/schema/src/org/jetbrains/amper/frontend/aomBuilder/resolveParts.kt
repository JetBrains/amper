/*
 * Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.aomBuilder

import org.jetbrains.amper.frontend.*

// Copy paste from "sources/frontend-api/src/org/jetbrains/amper/frontend/resolve/resolve.kt"
// TODO Need to be removed after parts replacement with Settings.

val Model.resolved: Model
    get() = ModelImpl(
        this@resolved.modules.map {
            object : PotatoModule by it {
                override val fragments = it.fragments.resolve(it)
            }
        }
    )


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
                val resolved = dependant.resolve(fragment, module)
                alreadyResolved.add(resolved.name)
                deque.add(resolved)
                add(resolved)
            }
        }
    }
}


fun Fragment.resolve(parent: Fragment, module: PotatoModule): Fragment {
    return resolveParts(parts + parent.parts, module)
}

fun Fragment.resolve(module: PotatoModule): Fragment {
    return resolveParts(parts, module)
}

@Suppress("UNCHECKED_CAST")
private fun Fragment.resolveParts(parts: ClassBasedSet<FragmentPart<*>>, module: PotatoModule): Fragment {
    val resolvedParts = parts.map {
        propagateFor(it as FragmentPart<Any>).default(module)
    }.toClassBasedSet()
    return createResolvedAdapter(resolvedParts)
}


fun Fragment.createResolvedAdapter(
    resolvedParts: ClassBasedSet<FragmentPart<*>>
) = if (this@createResolvedAdapter is LeafFragment)
    object : LeafFragment by this@createResolvedAdapter {
        override val parts: ClassBasedSet<FragmentPart<*>>
            get() = resolvedParts

        override fun equals(other: Any?) = other != null &&
                other is LeafFragment &&
                name == other.name

        override fun hashCode() = name.hashCode()
    }
else object : Fragment by this@createResolvedAdapter {
    override val parts: ClassBasedSet<FragmentPart<*>>
        get() = resolvedParts

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
