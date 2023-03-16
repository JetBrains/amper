package org.jetbrains.deft.proto.gradle

import org.jetbrains.deft.proto.frontend.Model
import org.jetbrains.deft.proto.frontend.PotatoModule

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
    val passedModule: PotatoModule
) : PotatoModule by passedModule {

    val artifactPlatforms by lazy { artifacts.flatMap { it.platforms }.toSet() }

}