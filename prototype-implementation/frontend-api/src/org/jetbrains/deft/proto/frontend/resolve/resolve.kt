package org.jetbrains.deft.proto.frontend.propagate

import org.jetbrains.deft.proto.frontend.*


val Model.resolved: Model
    get() = object : Model {
        override val modules: List<PotatoModule>
            get() = this@resolved.modules.map {
                object : PotatoModule by it {
                    override val fragments: List<Fragment>
                        get() = it.fragments.resolve()
                }
            }
    }


fun List<Fragment>.resolve(): List<Fragment> = buildList {
    var root: Fragment? = this@resolve.firstOrNull()
    while (root?.fragmentDependencies?.isNotEmpty() == true) {
        root = root.fragmentDependencies.firstOrNull()?.target
    }
    val deque = ArrayDeque<Fragment>()
    val alreadyResolved = mutableSetOf<String>()
    root?.let {
        add(it)
        deque.add(it)
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

@Suppress("UNCHECKED_CAST")
fun Fragment.resolve(parent: Fragment): Fragment {
    val parentAndThisParts = parts + parent.parts
    val resolvedParts = parentAndThisParts.map {
        propagateFor(it as FragmentPart<Any>).default()
    }.toClassBasedSet()
    return createResolvedAdapter(resolvedParts)
}

fun Fragment.createResolvedAdapter(
    resolvedParts: ClassBasedSet<FragmentPart<*>>
) = if (this@createResolvedAdapter is LeafFragment)
    object : LeafFragment by this@createResolvedAdapter {
        override val parts: ClassBasedSet<FragmentPart<*>>
            get() = resolvedParts
    }
else object : Fragment by this@createResolvedAdapter {
    override val parts: ClassBasedSet<FragmentPart<*>>
        get() = resolvedParts
}

fun Fragment.propagateFor(
    parentPart: FragmentPart<Any>
): FragmentPart<*> {
    val sourcePart = parts[parentPart::class.java]
    return if (parentPart !== sourcePart) {
        sourcePart?.propagate(parentPart) ?: parentPart
    } else sourcePart
}