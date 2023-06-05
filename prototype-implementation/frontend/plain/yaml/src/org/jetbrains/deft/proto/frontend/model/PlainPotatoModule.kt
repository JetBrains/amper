package org.jetbrains.deft.proto.frontend.model

import org.jetbrains.deft.proto.frontend.*
import kotlin.io.path.name

context (BuildFileAware, Stateful<FragmentBuilder, Fragment>)
internal class PlainPotatoModule(
    private val config: Settings,
    private val fragmentBuilders: List<FragmentBuilder>,
    private val artifactBuilders: List<ArtifactBuilder>,
    override val parts: ClassBasedSet<ModulePart<*>>,
) : PotatoModule {
    override val userReadableName: String
        get() = buildFile.parent.name
    override val type: PotatoModuleType
        get() = when (config.getByPath<String>("product", "type") ?: error("Product type is required")) {
            "app" -> PotatoModuleType.APPLICATION
            "lib" -> PotatoModuleType.LIBRARY
            else -> error("Unsupported product type")
        }
    override val source: PotatoModuleSource
        get() = PotatoModuleFileSource(buildFile)

    override val fragments: List<Fragment>
        get() = fragmentBuilders.immutableFragments

    override val artifacts: List<Artifact>
        get() = artifactBuilders.immutableArtifacts
}