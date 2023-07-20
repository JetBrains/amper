package org.jetbrains.deft.proto.frontend.model

import org.jetbrains.deft.proto.frontend.*
import kotlin.io.path.name

context (BuildFileAware, Stateful<FragmentBuilder, Fragment>)
internal class PlainPotatoModule(
    private val productType: ProductType,
    private val fragmentBuilders: List<FragmentBuilder>,
    private val artifactBuilders: List<ArtifactBuilder>,
    override val parts: ClassBasedSet<ModulePart<*>>,
) : PotatoModule {
    override val userReadableName: String
        get() = buildFile.parent.name
    override val type: PotatoModuleType
        get() = if (productType.isLibrary()) PotatoModuleType.LIBRARY else PotatoModuleType.APPLICATION
    override val source: PotatoModuleSource
        get() = PotatoModuleFileSource(buildFile)

    override val fragments: List<Fragment>
        get() = fragmentBuilders.immutableFragments

    override val artifacts: List<Artifact>
        get() = artifactBuilders.immutableArtifacts
}