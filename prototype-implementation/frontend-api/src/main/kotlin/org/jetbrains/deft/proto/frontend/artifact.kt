package org.jetbrains.deft.proto.frontend

sealed interface ArtifactPart<SelfT> {
    // Do not actually sure, if it is needed.
    fun merge(other: SelfT)
}

data class KotlinArtifactPart(
    val some: String
) : ArtifactPart<KotlinFragmentPart> {
    override fun merge(other: KotlinFragmentPart) {
        TODO("Not yet implemented")
    }
}

/**
 * Some resulting artifact that is built from several fragments.
 */
interface Artifact {
    val fragments: List<Fragment>
    val platforms: Set<Platform>
    val parts: ClassBasedSet<ArtifactPart<*>>
}