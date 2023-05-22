package org.jetbrains.deft.proto.gradle

import org.jetbrains.deft.proto.frontend.*

/**
 * A class, that caches [modules] and also, adds
 * some other functionality.
 */
data class ModelWrapper(
    val model: Model
) : Model {
    override val modules = ArrayList(model.modules)
}

data class BindPlatform(val platform: Platform, val artifact: Artifact)

class PotatoModuleWrapper(
    private val passedModule: PotatoModule
) : PotatoModule by passedModule {
    val artifactPlatforms by lazy { artifacts.flatMap { it.platforms }.toSet() }
    override val fragments = passedModule.fragments.map { FragmentWrapper(it) }
    val fragmentsByName by lazy { fragments.associateBy { it.name } }
    override val artifacts = passedModule.artifacts.map { it.wrap() }
    val nonTestArtifacts by lazy { artifacts.filter { it !is TestArtifact } }
}

fun Artifact.wrap() = if (this is TestArtifact) TestArtifactWrapper(this) else ArtifactWrapper(this)

interface PlatformAware {
    val platforms: Set<Platform>
}

@Suppress("LeakingThis")
open class ArtifactWrapper(
    artifact: Artifact
) : Artifact by artifact, PlatformAware {
    val bindPlatforms: Set<BindPlatform> = platforms.map { BindPlatform(it, this) }.toSet()
    // Actually, duplicating [FragmentWrapper] objects here but ok for prototyping.
    override val fragments = artifact.fragments.map { FragmentWrapper(it) }
}

class TestArtifactWrapper(
    artifact: TestArtifact
) : ArtifactWrapper(artifact), TestArtifact {
    override val testFor = artifact.testFor
}

class FragmentWrapper(
    private val fragment: Fragment
) : Fragment by fragment, PlatformAware {
}