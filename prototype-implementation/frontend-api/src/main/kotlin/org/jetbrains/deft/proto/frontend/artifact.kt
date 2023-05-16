package org.jetbrains.deft.proto.frontend

sealed interface ArtifactPart<SelfT> {
    fun default(): ArtifactPart<SelfT>
}

data class KotlinArtifactPart(
    val some: String
) : ArtifactPart<KotlinArtifactPart> {
    override fun default(): ArtifactPart<KotlinArtifactPart> = KotlinArtifactPart(some.ifBlank { "default" })

}

data class AndroidArtifactPart(
    val compileSdkVersion: String?,
    val minSdkVersion: Int?,

) : ArtifactPart<AndroidArtifactPart> {
    override fun default(): ArtifactPart<AndroidArtifactPart> =
        AndroidArtifactPart(compileSdkVersion ?: "android-33", minSdkVersion ?: 21)
}

data class JavaApplicationArtifactPart(
    val mainClass: String?,
    val packagePrefix: String?,
) : ArtifactPart<JavaApplicationArtifactPart> {
    override fun default(): ArtifactPart<JavaApplicationArtifactPart> =
        JavaApplicationArtifactPart(mainClass ?: "MainKt", packagePrefix ?: "")
}

data class NativeApplicationArtifactPart(
    val entryPoint: String?
) : ArtifactPart<NativeApplicationArtifactPart> {
    override fun default(): ArtifactPart<NativeApplicationArtifactPart> =
        NativeApplicationArtifactPart(entryPoint ?: "main")
}

data class PublicationFragmentPart(
    val group: String,
    val version: String,
) : ArtifactPart<PublicationFragmentPart> {
    override fun default(): ArtifactPart<PublicationFragmentPart> = PublicationFragmentPart(group, version)
}

/**
 * Some resulting artifact that is build from several leaf fragments.
 */
interface Artifact {
    val name: String

    // Only leaf fragment
    val fragments: List<Fragment>
    val platforms: Set<Platform>
    val parts: ClassBasedSet<ArtifactPart<*>>
    val isTest get() = this is TestArtifact
}

/**
 * Dependant test artifact.
 */
interface TestArtifact : Artifact {
    val testFor: Artifact
}