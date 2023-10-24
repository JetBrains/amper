package org.jetbrains.amper.frontend


/**
 * Some resulting artifact that is build from several leaf fragments.
 */
interface Artifact {
    val name: String
    val fragments: List<LeafFragment>
    val platforms: Set<Platform>
    val isTest get() = this is TestArtifact
}

/**
 * Dependant test artifact.
 */
interface TestArtifact : Artifact