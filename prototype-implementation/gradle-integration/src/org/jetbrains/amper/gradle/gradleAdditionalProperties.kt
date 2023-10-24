package org.jetbrains.amper.gradle

import org.gradle.api.Project
import org.gradle.api.invocation.Gradle
import org.gradle.api.plugins.ExtensionAware
import org.jetbrains.amper.frontend.Model
import java.nio.file.Path

private const val KNOWN_MODEL_EXT = "org.jetbrains.amper.gradle.ext.model"

var Gradle.knownModel: Model?
    get() = (this as ExtensionAware).extensions.extraProperties[KNOWN_MODEL_EXT] as? Model
    set(value) {
        (this as ExtensionAware).extensions.extraProperties[KNOWN_MODEL_EXT] = value
    }

private const val PROJECT_TO_MODULE_EXT = "org.jetbrains.amper.gradle.ext.projectToModule"

/**
 * Needed, because there is no [Project] during gradle setting setup, only [ProjectDescriptor],
 * so cant utilize [Project]'s [ExtensionAware] interface.
 */
val Gradle.projectPathToModule: MutableMap<String, PotatoModuleWrapper>
    get() = (this as ExtensionAware).extensions.extraProperties.getBindingMap(PROJECT_TO_MODULE_EXT)

private const val MODULE_TO_PROJECT_EXT = "org.jetbrains.amper.gradle.ext.moduleToProject"

/**
 * Needed, because there is no [Project] during gradle setting setup, only [ProjectDescriptor],
 * so cant utilize [Project]'s [ExtensionAware] interface.
 */
val Gradle.moduleFilePathToProject: MutableMap<Path, String>
    get() = (this as ExtensionAware).extensions.extraProperties.getBindingMap(MODULE_TO_PROJECT_EXT)