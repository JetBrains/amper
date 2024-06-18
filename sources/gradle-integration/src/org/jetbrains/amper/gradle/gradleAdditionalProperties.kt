/*
 * Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.gradle

import org.gradle.api.Project
import org.gradle.api.invocation.Gradle
import org.gradle.api.plugins.ExtensionAware
import org.gradle.api.plugins.ExtraPropertiesExtension
import org.jetbrains.amper.frontend.Model
import org.jetbrains.kotlin.gradle.plugin.extraProperties
import java.nio.file.Path

private const val KNOWN_MODEL_EXT = "org.jetbrains.amper.gradle.ext.model"

var Gradle.knownModel: Model?
    get() = extraProperties.getOrNull<Model>(KNOWN_MODEL_EXT)
    set(value) {
        extraProperties[KNOWN_MODEL_EXT] = value
    }

private const val AMPER_MODULE_EXT = "org.jetbrains.amper.project.ext.amperModule"

/**
 * The Amper module corresponding to this Gradle [Project].
 */
var Project.amperModule: PotatoModuleWrapper?
    get() = extraProperties.getOrNull<PotatoModuleWrapper>(AMPER_MODULE_EXT)
    set(value) {
        extraProperties[AMPER_MODULE_EXT] = value
    }

private const val MODULE_TO_PROJECT_EXT = "org.jetbrains.amper.gradle.ext.moduleToProject"

/**
 * Needed, because there is no [Project] during gradle setting setup, only [ProjectDescriptor],
 * so cant utilize [Project]'s [ExtensionAware] interface.
 */
val Gradle.moduleFilePathToProject: MutableMap<Path, String>
    get() = extraProperties.getBindingMap(MODULE_TO_PROJECT_EXT)

private inline fun <reified T : Any> ExtraPropertiesExtension.getOrNull(name: String): T? {
    return if (has(name)) get(name) as T else null
}
