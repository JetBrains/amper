package org.jetbrains.amper.frontend.model

import org.jetbrains.amper.frontend.*
import kotlin.io.path.name

context (BuildFileAware, Stateful<FragmentBuilder, Fragment>, TypesafeVariants)
internal class PlainPotatoModule(
    private val productType: ProductType,
    private val fragmentBuilders: List<FragmentBuilder>,
    private val artifactBuilders: List<ArtifactBuilder>,
    override val parts: ClassBasedSet<ModulePart<*>>,
) : PotatoModule {
    override val userReadableName: String
        get() = buildFile.parent.name
    override val type: ProductType
        get() = productType
    override val source: PotatoModuleSource
        get() = PotatoModuleFileSource(buildFile)

    override val fragments: List<Fragment>
        get() = fragmentBuilders.immutableFragments

    override val artifacts: List<Artifact>
        get() = artifactBuilders.immutableArtifacts
}