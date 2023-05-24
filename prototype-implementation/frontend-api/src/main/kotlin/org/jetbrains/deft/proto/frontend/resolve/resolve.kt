package org.jetbrains.deft.proto.frontend.propagate

import org.jetbrains.deft.proto.frontend.*
import java.nio.file.Path


val Model.resolved: Model
    get() = object : Model {
        override val parts: ClassBasedSet<ModelPart<*>>
            get() = this@resolved.parts

        override val modules: List<PotatoModule>
            get() = this@resolved.modules.map {
                object : PotatoModule {
                    override val userReadableName: String
                        get() = it.userReadableName
                    override val type: PotatoModuleType
                        get() = it.type
                    override val source: PotatoModuleSource
                        get() = it.source
                    override val fragments: List<Fragment>
                        get() = it.fragments.resolve { propagate(it).default() }
                    override val artifacts: List<Artifact>
                        get() = it.artifacts.resolve { default() }

                }
            }
    }


fun List<Fragment>.resolve(block: FragmentPart<Any>.(FragmentPart<*>) -> FragmentPart<*>): List<Fragment> = buildList {
    val deque = ArrayDeque<Fragment>()
    this@resolve.firstOrNull { it.name == "common" }?.let {
        add(it)
        deque.add(it)
    } ?: run {
        this@resolve.firstOrNull()?.let {
            add(it)
            deque.add(it)
        }
    }

    while (deque.isNotEmpty()) {
        val fragment = deque.removeFirst()
        fragment.fragmentDependants.forEach { link ->
            val dependant = link.target
            if (dependant !in this) {
                val resolved = dependant.resolve(fragment, block)
                deque.add(resolved)
                add(resolved)
            }
        }
    }
}

fun List<Artifact>.resolve(block: ArtifactPart<Any>.() -> ArtifactPart<*>): List<Artifact> = map { it.resolve(block) }

@Suppress("UNCHECKED_CAST")
fun Fragment.resolve(parent: Fragment, block: FragmentPart<Any>.(FragmentPart<*>) -> FragmentPart<*>): Fragment {
    val resolvedParts = parent.parts.map {
        val parentPart = it
        val sourcePart = parts[parentPart::class.java]
        sourcePart?.let {
            (it as FragmentPart<Any>).block(parentPart)
        } ?: (parentPart as FragmentPart<Any>).block(parentPart)
    }.toClassBasedSet()
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

@Suppress("UNCHECKED_CAST")
fun Artifact.resolve(block: ArtifactPart<Any>.() -> ArtifactPart<*>): Artifact {
    val resolvedParts = parts.map { (it as ArtifactPart<Any>).block() }.toClassBasedSet()
    if (this is TestArtifact) {
        return object: TestArtifact {
            override val testFor: Artifact
                get() = this@resolve.testFor
            override val name: String
                get() = this@resolve.name
            override val fragments: List<Fragment>
                get() = this@resolve.fragments
            override val platforms: Set<Platform>
                get() = this@resolve.platforms
            override val parts: ClassBasedSet<ArtifactPart<*>>
                get() = resolvedParts
        }
    }
    return object : Artifact {
        override val name: String
            get() = this@resolve.name
        override val fragments: List<Fragment>
            get() = this@resolve.fragments
        override val platforms: Set<Platform>
            get() = this@resolve.platforms
        override val parts: ClassBasedSet<ArtifactPart<*>>
            get() = resolvedParts
    }
}