package org.jetbrains.deft.proto.gradle

import org.gradle.api.Project
import org.gradle.api.invocation.Gradle
import org.gradle.api.plugins.ExtensionAware
import org.jetbrains.deft.proto.frontend.Model
import org.jetbrains.deft.proto.frontend.PotatoModule
import java.nio.file.Path

private const val KNOWN_MODEL_EXT = "org.jetbrains.deft.proto.gradle.ext.model"

var Gradle.knownModel: Model?
    get() = (this as ExtensionAware).extensions.extraProperties[KNOWN_MODEL_EXT] as? Model
    set(value) {
        (this as ExtensionAware).extensions.extraProperties[KNOWN_MODEL_EXT] = value
    }

private const val PROJECT_TO_MODULE_EXT = "org.jetbrains.deft.proto.gradle.ext.projectToModule"

/**
 * Needed, because there is no [Project] during gradle setting setup, only [ProjectDescriptor],
 * so cant utilize [Project]'s [ExtensionAware] interface.
 */
@Suppress("UNCHECKED_CAST")
var Gradle.projectPathToModule: Map<String, PotatoModuleWrapper>
    get() = (this as ExtensionAware).extensions.extraProperties[PROJECT_TO_MODULE_EXT] as? Map<String, PotatoModuleWrapper>
        ?: emptyMap()
    set(value) {
        (this as ExtensionAware).extensions.extraProperties[PROJECT_TO_MODULE_EXT] = value
    }

private const val MODULE_TO_PROJECT_EXT = "org.jetbrains.deft.proto.gradle.ext.moduleToProject"

/**
 * Needed, because there is no [Project] during gradle setting setup, only [ProjectDescriptor],
 * so cant utilize [Project]'s [ExtensionAware] interface.
 */
@Suppress("UNCHECKED_CAST")
var Gradle.moduleFilePathToProject: Map<Path, String>
    get() = (this as ExtensionAware).extensions.extraProperties[MODULE_TO_PROJECT_EXT] as? Map<Path, String>
        ?: emptyMap()
    set(value) {
        (this as ExtensionAware).extensions.extraProperties[MODULE_TO_PROJECT_EXT] = value
    }