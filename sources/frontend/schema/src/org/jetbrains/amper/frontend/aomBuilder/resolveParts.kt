/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.aomBuilder

import org.jetbrains.amper.frontend.Fragment
import org.jetbrains.amper.frontend.LeafFragment
import org.jetbrains.amper.frontend.Model
import org.jetbrains.amper.frontend.PotatoModule
import org.jetbrains.amper.frontend.processing.merge
import org.jetbrains.amper.frontend.schema.Settings

// TODO move to fragments creation (build.kt / fragments.kt)

internal class ModelImpl(override val modules: List<PotatoModule>) : Model

val Model.resolved: Model
    get() = ModelImpl(
        this@resolved.modules.map { it.withResolvedFragments() }
    )

fun PotatoModule.withResolvedFragments(): PotatoModule = object : PotatoModule by this {
    override val fragments: List<Fragment> = this@withResolvedFragments.fragments.resolve()
}

private fun List<Fragment>.resolve(): List<Fragment> = buildList {
    var root: Fragment? = this@resolve.firstOrNull()
    while (root?.fragmentDependencies?.isNotEmpty() == true) {
        root = root.fragmentDependencies.firstOrNull()?.target
    }
    val deque = ArrayDeque<Fragment>()
    val alreadyResolved = mutableSetOf<String>()
    root?.let {
        val resolvedFragment = it.resolve()
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
                val resolved = dependant.resolve(fragment)
                alreadyResolved.add(resolved.name)
                deque.add(resolved)
                add(resolved)
            }
        }
    }
}

private fun Fragment.resolve(parent: Fragment? = null): Fragment {
    return createResolvedAdapter(mergedSettings = parent?.settings?.merge(settings) ?: settings)
}

private fun Fragment.createResolvedAdapter(mergedSettings: Settings) = when (this@createResolvedAdapter) {
    is LeafFragment -> object : LeafFragment by this@createResolvedAdapter {
        override val settings = mergedSettings
        override fun equals(other: Any?) = other != null && other is LeafFragment && name == other.name
        override fun hashCode() = name.hashCode()
    }
    else -> object : Fragment by this@createResolvedAdapter {
        override val settings = mergedSettings
        override fun equals(other: Any?) = other != null && other is LeafFragment && name == other.name
        override fun hashCode() = name.hashCode()
    }
}
