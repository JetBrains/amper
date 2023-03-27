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
    override val artifacts = passedModule.artifacts.map { ArtifactWrapper(it) }

    val androidNeeded = artifactPlatforms.contains(Platform.ANDROID)
}

class ArtifactWrapper(
    private val artifact: Artifact
) : Artifact by artifact {
    // Actually, duplicating [FragmentWrapper] objects here but ok for prototyping.
    override val fragments = artifact.fragments.map { FragmentWrapper(it) }
    private val partsByClass = parts.associate { it.clazz to it.value }
    operator fun <T : Any> get(clazz: Class<T>) = partsByClass[clazz]?.let { it as T }
    inline fun <reified T : Any> part() = this[T::class.java]
}

class FragmentWrapper(
    private val fragment: Fragment
) : Fragment by fragment {
    private val partsByClass = parts.associate { it.clazz to it.value }
    operator fun <T : Any> get(clazz: Class<T>) = partsByClass[clazz]?.let { it as T }
    inline fun <reified T : Any> part() = this[T::class.java]
}