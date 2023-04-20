package org.jetbrains.deft.proto.gradle

import org.gradle.api.plugins.ExtraPropertiesExtension
import org.jetbrains.deft.proto.frontend.Platform
import org.jetbrains.deft.proto.frontend.PotatoModule
import org.jetbrains.deft.proto.frontend.PotatoModuleFileSource
import java.util.*

val PotatoModule.buildFile get() = (source as PotatoModuleFileSource).buildFile

val PotatoModule.buildDir get() = buildFile.parent

/**
 * Get or create string key-ed binding map from extension properties.
 */
@Suppress("UNCHECKED_CAST")
fun <K, V> ExtraPropertiesExtension.getBindingMap(name: String) = try {
    this[name] as MutableMap<K, V>
} catch (cause: ExtraPropertiesExtension.UnknownPropertyException) {
    val bindingMap = mutableMapOf<K, V>()
    this[name] = bindingMap
    bindingMap
}

/**
 * Check if the requested platform is included in module.
 */
operator fun PotatoModuleWrapper.contains(platform: Platform) =
    artifactPlatforms.contains(platform)

val PotatoModuleWrapper.androidNeeded get() = Platform.ANDROID in this
val PotatoModuleWrapper.javaNeeded get() = Platform.JVM in this