package org.jetbrains.deft.proto.frontend.model

import org.jetbrains.deft.proto.frontend.*

context (Stateful<FragmentBuilder, Fragment>)
internal open class PlainArtifact(private val artifactBuilder: ArtifactBuilder) : Artifact {
    override val name: String
        get() = artifactBuilder.name
    override val fragments: List<Fragment>
        get() = artifactBuilder.fragments.immutableFragments
    override val platforms: Set<Platform>
        get() = artifactBuilder.platforms
    override val parts: ClassBasedSet<ArtifactPart<*>>
        get() = buildClassBasedSet {
            add(
                AndroidArtifactPart(
                    artifactBuilder.android?.compileSdkVersion,
                    artifactBuilder.android?.androidMinSdkVersion,
                    artifactBuilder.android?.sourceCompatibility,
                    artifactBuilder.android?.targetCompatibility,
                )
            )

            add(
                JavaArtifactPart(
                    artifactBuilder.java?.mainClass,
                    artifactBuilder.java?.packagePrefix,
                    artifactBuilder.java?.jvmTarget,
                )
            )

            add(
                PublicationArtifactPart(
                    artifactBuilder.publish?.group,
                    artifactBuilder.publish?.version,
                )
            )

            add(
                NativeApplicationArtifactPart(
                    artifactBuilder.native?.entryPoint
                )
            )
        }
}


context (Stateful<FragmentBuilder, Fragment>)
internal class TestPlainArtifact(artifactBuilder: ArtifactBuilder) : PlainArtifact(artifactBuilder),
    TestArtifact