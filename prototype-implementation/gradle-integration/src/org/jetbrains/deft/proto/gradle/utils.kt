package org.jetbrains.deft.proto.gradle

import org.gradle.api.plugins.ExtraPropertiesExtension
import org.jetbrains.deft.proto.frontend.Platform
import org.jetbrains.deft.proto.frontend.PotatoModule
import org.jetbrains.deft.proto.frontend.PotatoModuleFileSource
import org.jetbrains.deft.proto.gradle.base.BindingPluginPart
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSet
import org.jetbrains.kotlin.tooling.core.withClosure
import org.slf4j.Logger
import java.io.File
import java.nio.file.Path
import kotlin.io.path.*

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

/**
 * Try to find entry point for application.
 */
// TODO Add caching for separated fragments.
enum class EntryPointType(val symbolName: String) { NATIVE("main"), JVM("MainKt") }

val BindingPluginPart.hasGradleScripts
    get() = module.hasGradleScripts

val PotatoModule.hasGradleScripts
    get() = buildDir.run {
        resolve("build.gradle.kts").exists() || resolve("build.gradle").exists()
    }

val KotlinSourceSet.closure: Set<KotlinSourceSet> get() = this
    .withClosure { it.dependsOn }

val KotlinSourceSet.closureSources: List<Path> get() = this.closure
    .flatMap { it.kotlin.srcDirs }
    .map { it.toPath() }
    .filter { it.exists() }

@OptIn(ExperimentalPathApi::class)
internal fun findEntryPoint(
    sources: List<Path>,
    entryPointType: EntryPointType,
    logger: Logger,
): String {
    val implicitMainFile = sources.firstNotNullOfOrNull { sourceFolder ->
        sourceFolder.walk(PathWalkOption.BREADTH_FIRST)
            .find { it.name.equals("main.kt", ignoreCase = true) }
            ?.normalize()
            ?.toAbsolutePath()
    }

    if (implicitMainFile == null) {
        val result = entryPointType.symbolName
        logger.warn(
            "Entry point cannot be discovered for sources ${sources.joinToString { "$it" }}. " +
                    "Defaulting to $result"
        )
        return result
    }

    val packageRegex = "^package\\s+([\\w.]+)".toRegex(RegexOption.MULTILINE)
    val pkg = packageRegex.find(implicitMainFile.readText())?.let { it.groupValues[1].trim() }

    val result = if (pkg != null) "$pkg.${entryPointType.symbolName}" else entryPointType.symbolName

    logger.info("Entry point discovered at $result")
    return result
}

/**
 * Set the property if its value is missing.
 */
fun trySetSystemProperty(key: String, value: String) {
    if (System.getProperty(key) == null)
        System.setProperty(key, value)
}

/**
 * Replace last path entry that is matched by [matcher] by given name.
 */
fun File.replaceLast(newName: String, matcher: (String) -> Boolean): File {
    val nonMatching = mutableListOf<String>()
    var current = this
    while (!matcher(current.name)) {
        nonMatching.add(current.name)
        current = current.parentFile ?: return this
    }
    val newBase = current.parentFile?.resolve(newName) ?: File(newName)
    return nonMatching.foldRight(newBase) { it, acc -> acc.resolve(it) }
}

/**
 * Replace last path entry that is matched by [matcher] by given name.
 */
fun Path.replaceLast(newName: String, matcher: (String) -> Boolean): Path {
    val nonMatching = mutableListOf<String>()
    var current = this
    while (!matcher(current.name)) {
        nonMatching.add(current.name)
        current = current.parent ?: return this
    }
    val newBase = current.parent?.resolve(newName) ?: Path(newName)
    return nonMatching.foldRight(newBase) { it, acc -> acc.resolve(it) }
}