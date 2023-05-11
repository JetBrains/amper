package org.jetbrains.deft.proto.frontend.propagate

import org.jetbrains.deft.proto.frontend.*
import java.nio.file.Path

typealias Parts = Set<ByClassWrapper<FragmentPart<*>>>

val Model.propagatedFragments: Model
    get() = object : Model {
        override val modules: List<PotatoModule>
            get() = this@propagatedFragments.modules.map {
                object : PotatoModule {
                    override val userReadableName: String
                        get() = it.userReadableName
                    override val type: PotatoModuleType
                        get() = it.type
                    override val source: PotatoModuleSource
                        get() = it.source
                    override val fragments: List<Fragment>
                        get() = it.fragments.propagateFragmentTree { propagate(it).default() }
                    override val artifacts: List<Artifact>
                        get() = it.artifacts

                }
            }
    }


fun List<Fragment>.propagateFragmentTree(block: FragmentPart<Any>.(FragmentPart<*>) -> FragmentPart<*>): List<Fragment> =
    buildList {
        val deque = ArrayDeque<Fragment>()
        this@propagateFragmentTree.firstOrNull { it.name == "common" }?.let {
            add(it)
            deque.add(it)
        }

        while (deque.isNotEmpty()) {
            val fragment = deque.removeFirst()
            fragment.fragmentDependants.forEach { link ->
                val dependant = link.target
                if (dependant !in deque) {
                    val resolved = dependant.resolve(fragment, block)
                    deque.add(resolved)
                    add(resolved)
                }
            }
        }
    }

@Suppress("UNCHECKED_CAST")
fun Fragment.resolve(parent: Fragment, block: FragmentPart<Any>.(FragmentPart<*>) -> FragmentPart<*>): Fragment {
    val resolvedParts = parent.parts.mapNotNull {
        val parentPart = it.value
        val sourcePart = parts.find { it.value::class == parentPart::class }?.value
        sourcePart?.let {
            ByClassWrapper((it as FragmentPart<Any>).block(parentPart))
        } ?: ByClassWrapper((parentPart as FragmentPart<Any>).block(parentPart))
    }.toSet()
    return object : Fragment {
        override val name: String
            get() = this@resolve.name
        override val fragmentDependencies: List<FragmentLink>
            get() = this@resolve.fragmentDependencies
        override val fragmentDependants: List<FragmentLink>
            get() = this@resolve.fragmentDependants
        override val externalDependencies: List<Notation>
            get() = this@resolve.externalDependencies
        override val parts: ClassBasedSet<FragmentPart<*>>
            get() = resolvedParts
        override val platforms: Set<Platform>
            get() = this@resolve.platforms
        override val src: Path?
            get() = this@resolve.src
    }
}
