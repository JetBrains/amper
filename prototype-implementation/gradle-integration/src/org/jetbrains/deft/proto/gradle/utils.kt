package org.jetbrains.deft.proto.gradle

import org.gradle.api.plugins.ExtraPropertiesExtension
import org.jetbrains.deft.proto.frontend.*
import org.jetbrains.deft.proto.gradle.base.DeftNamingConventions
import java.nio.file.Path
import kotlin.io.path.*

val PotatoModule.buildFile get() = (source as PotatoModuleFileSource).buildFile

val PotatoModule.buildDir get() = buildFile.parent

val PotatoModule.additionalScript
    get() = buildDir
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

val PotatoModuleWrapper.composeNeeded: Boolean get() = leafFragments.any { it.parts.find<ComposePart>()?.enabled == true }

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

data class FoundEntryPoint(
    val file: Path,
    val mainName: String,
    val pkg: String?,
)

/**
 * Try to find entry point for application.
 */
// TODO Add caching for separated fragments.
context(DeftNamingConventions)
@OptIn(ExperimentalPathApi::class)
internal fun findEntryPoint(
    mainFileName: String,
    fragment: LeafFragmentWrapper,
    caseSensitive: Boolean = false,
) = with(module) {
    // Collect all fragment paths.
    val allSources = buildSet<Path> {
        fragment.forClosure {
            add(it.wrapped.sourcePath.absolute().normalize())
        }
    }

    val collectedMains = allSources.flatMap { path ->
        path.walk()
            .filter {
                if (caseSensitive)
                    it.name == mainFileName
                else
                    it.name.lowercase() == mainFileName.lowercase()
            }
            .map { it.normalize().toAbsolutePath() }
    }

    val implicitMainFile = when {
        collectedMains.size > 1 -> error(
            "Cannot define entry point for fragment ${fragment.name} implicitly: " +
                    "there is more that one $mainFileName file. " +
                    "Specify it explicitly or remove all but one."
        )

        collectedMains.isEmpty() -> error(
            "Cannot define entry point for fragment ${fragment.name} implicitly: " +
                    "there is no $mainFileName file. " +
                    "Specify it explicitly or add one."
        )

        else -> collectedMains.single()
    }

    val packageRegex = "^package( [^$]+)$".toRegex()
    val matchResult = packageRegex.find(implicitMainFile.readText())

    return@with FoundEntryPoint(
        implicitMainFile,
        mainFileName,
        matchResult?.let { it.groupValues[1].trim() }
    )
}