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

class PotatoModuleWrapper(
    private val passedModule: PotatoModule
) : PotatoModule by passedModule {
    val artifactPlatforms by lazy { artifacts.flatMap { it.platforms }.toSet() }
    override val fragments = passedModule.fragments.map { FragmentWrapper(it) }
    override val artifacts = passedModule.artifacts.map { it.wrap() }
}

fun Artifact.wrap() = if (this is TestArtifact) TestArtifactWrapper(this) else ArtifactWrapper(this)

data class BindPlatform(val platform: Platform, val artifact: Artifact)

@Suppress("LeakingThis")
open class ArtifactWrapper(
    private val artifact: Artifact
) : Artifact by artifact {
    // Actually, duplicating [FragmentWrapper] objects here but ok for prototyping.
    override val fragments = artifact.fragments.map { FragmentWrapper(it) }
    private val partsByClass = parts.associate { it.clazz to it.value }
    val bindPlatforms: Set<BindPlatform> = platforms.map { BindPlatform(it, this) }.toSet()
    operator fun <T : Any> get(clazz: Class<T>) = partsByClass[clazz]?.let { it as T }
}

internal inline fun <reified T : Any> ArtifactWrapper.part() = this[T::class.java]

class TestArtifactWrapper(
    private val artifact: TestArtifact
) : ArtifactWrapper(artifact), TestArtifact by artifact {
    override val fragments = super.fragments
    override val name = super.name
    override val parts = super.parts
    override val platforms = super.platforms
}

class FragmentWrapper(
    private val fragment: Fragment
) : Fragment by fragment {
    private val partsByClass = parts.associate { it.clazz to it.value }
    operator fun <T : Any> get(clazz: Class<T>) = partsByClass[clazz]?.let { it as T }
}

internal inline fun <reified T : Any> FragmentWrapper.part() = this[T::class.java]