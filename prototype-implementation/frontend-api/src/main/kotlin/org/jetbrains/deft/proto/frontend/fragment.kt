package org.jetbrains.deft.proto.frontend

/**
 * Just a renaming for possible future class introduction.
 */
typealias Notation = String

enum class FragmentDependencyType {
    REFINE,
    FRIEND
}

/**
 * Dependency between fragments.
 * Can differ by type (refines dependency, test on sources dependency, etc.).
 */
interface FragmentDependency {
    val target: Fragment
    val type: FragmentDependencyType
}

/**
 * Some part of module, that supports "single resolve context" invariant for
 * every source and resource file, that is included.
 */
interface Fragment {
    val dependsOn: FragmentDependency?

    val dependencies: List<Notation>

    val platforms: Set<Platform>
}