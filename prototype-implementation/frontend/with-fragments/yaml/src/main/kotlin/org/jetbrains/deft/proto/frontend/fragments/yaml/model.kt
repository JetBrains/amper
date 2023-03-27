package org.jetbrains.deft.proto.frontend.fragments.yaml

import org.jetbrains.deft.proto.frontend.*

internal data class PotatoModuleImpl(
    override val userReadableName: String,
    override val type: PotatoModuleType,
    override val source: PotatoModuleSource,
    override val fragments: List<FragmentImpl>,
    override val artifacts: List<ArtifactImpl>,
) : PotatoModule

internal data class FragmentImpl(
    override val name: String,
    override val fragmentDependencies: MutableList<FragmentDependencyImpl>,
    override val externalDependencies: List<Notation>,
    override val parts: ClassBasedSet<FragmentPart<*>> = emptySet()
) : Fragment

internal data class FragmentDependencyImpl(
    override val target: FragmentImpl,
    override val type: FragmentDependencyType,
) : FragmentDependency

internal data class ArtifactImpl(
    override val name: String,
    override val fragments: List<FragmentImpl>,
    override val platforms: Set<Platform>,
    override val parts: ClassBasedSet<ArtifactPart<*>> = emptySet()
) : Artifact

internal data class FragmentDefinition(
    val externalDependencies: List<Notation>,
    val fragmentDependencies: List<String>,
)

internal data class Flavor(val values: List<String>)