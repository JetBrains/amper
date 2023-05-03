package org.jetbrains.deft.proto.frontend

sealed interface ArtifactPart<SelfT>

data class KotlinArtifactPart(
    val some: String
) : ArtifactPart<KotlinFragmentPart>

data class AndroidArtifactPart(
    val compileSdkVersion: String?,
) : ArtifactPart<AndroidArtifactPart>

data class JavaApplicationArtifactPart(
    val mainClass: String?,
    val packagePrefix: String?,
) : ArtifactPart<JavaApplicationArtifactPart>

data class NativeApplicationArtifactPart(
    val entryPoint: String?
) : ArtifactPart<NativeApplicationArtifactPart>

data class PublicationFragmentPart(
    val group: String,
    val version: String,
) : ArtifactPart<PublicationFragmentPart>

/**
 * Some resulting artifact that is build from several leaf fragments.
 */
interface Artifact {
    val name: String

    // Only leaf fragment
    val fragments: List<Fragment>
    val platforms: Set<Platform>
    val parts: ClassBasedSet<ArtifactPart<*>>
}

/**
 * Dependant test artifact.
 */
interface TestArtifact : Artifact {
    val testFor: Artifact
}