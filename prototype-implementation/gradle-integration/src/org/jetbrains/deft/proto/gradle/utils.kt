package org.jetbrains.deft.proto.gradle

import org.gradle.api.plugins.ExtraPropertiesExtension
import org.jetbrains.deft.proto.frontend.Platform
import org.jetbrains.deft.proto.frontend.PotatoModule
import org.jetbrains.deft.proto.frontend.PotatoModuleFileSource
import kotlin.io.path.exists

val PotatoModule.buildFile get() = (source as PotatoModuleFileSource).buildFile

val PotatoModule.buildDir get() = buildFile.parent

val PotatoModule.additionalScript get() = buildDir
    .resolve("Pot.gradle.kts")
    .takeIf { it.exists() }

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

/**
 * Try extract zero or single element from collection,
 * running [onMany] in other case.
 */
fun <T> Collection<T>.singleOrZero(onMany: () -> Unit): T? =
    if (size > 1) onMany().run { null }
    else singleOrNull()

/**
 * Require exact one element, throw error otherwise.
 */
fun <T> Collection<T>.requireSingle(errorMessage: () -> String): T =
    if (size > 1 || isEmpty()) error(errorMessage())
    else first()