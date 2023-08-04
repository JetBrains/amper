package org.jetbrains.deft.proto.frontend

import java.nio.file.Path


/**
 * Some part of the module that supports "single resolve context" invariant for
 * every source and resource file that is included.
 */
interface Fragment {
    val name: String
    val fragmentDependencies: List<FragmentLink>
    val fragmentDependants: List<FragmentLink>
    val externalDependencies: List<Notation>
    val parts: ClassBasedSet<FragmentPart<*>>
    val platforms: Set<Platform>
    val isTest: Boolean

    /**
     * Is this fragment is chosen by default when
     * no variants are specified?
     */
    val isDefault: Boolean
    val src: Path?

    val variants: List<String>
}

/**
 * Leaf fragment must have only one platform.
 * Also, it should contain parts, that are specific
 * for concrete artifacts.
 *
 * Each result artifact is specified by single leaf fragment
 * (Except for KMP libraries).
 */
interface LeafFragment : Fragment {
    val platform: Platform
}

sealed interface Notation

interface DefaultScopedNotation : Notation {
    val compile: Boolean get() = true
    val runtime: Boolean get() = true
    val exported: Boolean get() = false
}

interface PotatoModuleDependency : DefaultScopedNotation {
    // A dirty hack to make module resolution lazy.
    val Model.module: PotatoModule
}

data class MavenDependency(
    val coordinates: String,
    override val compile: Boolean = true,
    override val runtime: Boolean = true,
    override val exported: Boolean = false,
) : DefaultScopedNotation

enum class FragmentDependencyType {
    REFINE, FRIEND,
}

/**
 * Dependency between fragments.
 * Can differ by type (refines dependency, test on sources dependency, etc.).
 */
interface FragmentLink {
    val target: Fragment
    val type: FragmentDependencyType
}

sealed interface FragmentPart<SelfT> {

    // Default propagation is overwriting.
    fun propagate(parent: SelfT): FragmentPart<*> = this
    fun default(module: PotatoModule): FragmentPart<*>
}
