package org.jetbrains.deft.proto.frontend

/**
 * Some resulting artifact that is built from several fragments.
 */
interface Artifact {
    val fragments: List<Fragment>

    val platforms: Set<Platform>
}

interface Model {
    val modules: List<PotatoModule>
}