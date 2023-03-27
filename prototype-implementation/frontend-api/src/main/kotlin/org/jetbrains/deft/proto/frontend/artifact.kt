package org.jetbrains.deft.proto.frontend

sealed interface ArtifactPart<SelfT>

data class KotlinArtifactPart(
    val some: String
) : ArtifactPart<KotlinFragmentPart>

data class AndroidArtifactPart(
    val compileSdkVersion: String,
) : ArtifactPart<AndroidArtifactPart>

/**
 * Some resulting artifact that is built from several fragments.
 */
interface Artifact {
    val name: String
    val fragments: List<Fragment>
    val platforms: Set<Platform>
    val parts: ClassBasedSet<ArtifactPart<*>>
}