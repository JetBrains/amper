package org.jetbrains.deft.proto.frontend.fragments.yaml

import org.jetbrains.deft.proto.frontend.*
import java.nio.file.Path

internal data class PotatoModuleImpl(
        override val userReadableName: String,
        override val type: PotatoModuleType,
        override val source: PotatoModuleSource,
        override val fragments: List<FragmentImpl>,
        override val artifacts: List<ArtifactImpl>,
        override val parts: ClassBasedSet<ModulePart<*>>,
) : PotatoModule

internal data class FragmentImpl(
    override val name: String,
    override val fragmentDependencies: MutableList<FragmentLinkImpl>,
    override val externalDependencies: List<Notation>,
    override val parts: ClassBasedSet<FragmentPart<*>> = classBasedSet(),
    override val fragmentDependants: List<FragmentLink>,
    override val src: Path?,
    override val platforms: Set<Platform>,
) : Fragment

internal data class FragmentLinkImpl(
    override val target: FragmentImpl,
    override val type: FragmentDependencyType,
) : FragmentLink

internal data class ArtifactImpl(
    override val name: String,
    override val fragments: List<FragmentImpl>,
    override val platforms: Set<Platform>,
    override val parts: ClassBasedSet<ArtifactPart<*>> = classBasedSet(),
) : Artifact

internal data class FragmentDefinition(
    val externalDependencies: List<Notation>,
    val fragmentDependencies: List<String>,
    val fragmentParts: ClassBasedSet<FragmentPart<*>>,
    val artifactParts: ClassBasedSet<ArtifactPart<*>> = classBasedSet(),
)

internal data class Variant(val values: List<String>)

internal data class InnerDependency(val dependencyPath: Path): PotatoModuleDependency {
    override val Model.module: PotatoModule
        get() = modules.find {
            val source = it.source as? PotatoModuleFileSource ?: return@find false
            source.buildFile == dependencyPath
        } ?: error("No module found at $dependencyPath")
}
