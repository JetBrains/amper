package org.jetbrains.deft.proto.frontend.model

import org.jetbrains.deft.proto.frontend.*

context (Stateful<FragmentBuilder, Fragment>)
internal open class PlainArtifact(
    private val artifactBuilder: ArtifactBuilder
) : Artifact {
    override val name: String
        get() = artifactBuilder.name
    override val fragments: List<LeafFragment>
        get() = artifactBuilder.fragments.immutableLeafFragments
    override val platforms: Set<Platform>
        get() = artifactBuilder.platforms
}


context (Stateful<FragmentBuilder, Fragment>)
internal class TestPlainArtifact(artifactBuilder: ArtifactBuilder) : PlainArtifact(artifactBuilder),
    TestArtifact