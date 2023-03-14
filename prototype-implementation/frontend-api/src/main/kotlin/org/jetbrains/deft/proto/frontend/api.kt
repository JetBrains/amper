package org.jetbrains.deft.proto.frontend

typealias Notation = String
/**
 * Dependency between fragments.
 * Can differ by type (refines dependency, test on sources dependency, etc.).
 */
interface FragmentDependency {
    val target: Fragment

    // TODO Think about type: enum?
    val type: String
}

/**
 * Some part of module, that supports "single resolve context" invariant for
 * every source and resource file, that is included.
 */
interface Fragment {
    val dependsOn: FragmentDependency?

    val dependencies: List<Notation>
}

/**
 * Some resulting artifact that is built from several fragments.
 */
interface Artifact {
    val fragments: List<Fragment>
}

/**
 * Just an aggregator for fragments and artifacts.
 */
interface PotatoModule {
    val fragments: List<Fragment>
    val artifacts: List<Artifact>
}

interface Model {
    val modules: List<PotatoModule>
}