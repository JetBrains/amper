package org.jetbrains.amper.gradle

import org.gradle.api.file.SourceDirectorySet
import org.gradle.api.plugins.ExtraPropertiesExtension
import org.jetbrains.amper.frontend.MetaModulePart
import org.jetbrains.amper.frontend.Platform
import org.jetbrains.amper.frontend.PotatoModule
import org.jetbrains.amper.frontend.PotatoModuleFileSource
import org.jetbrains.amper.gradle.base.BindingPluginPart
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSet
import org.jetbrains.kotlin.tooling.core.withClosure
import org.slf4j.Logger
import java.io.File
import java.nio.file.Path
import kotlin.io.path.*

val PotatoModule.buildFile get() = (source as PotatoModuleFileSource).buildFile

val PotatoModule.buildDir get() = buildFile.parent

val BindingPluginPart.layout
    get() = (module.parts.find<MetaModulePart>()
        ?: error("No mandatory MetaModulePart in the module ${module.userReadableName}"))
        .layout

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

fun replacePenultimatePaths(
    sources: SourceDirectorySet,
    resources: SourceDirectorySet,
    newName: String
) {
    sources.setSrcDirs(sources.srcDirs.map { it.replacePenultimate(newName) })
    resources.setSrcDirs(resources.srcDirs.map { it.replacePenultimate(newName) })
}

/**
 * Replace penultimate path entry that is matched by [matcher] by given name.
 */
fun File.replacePenultimate(newName: String): File {
    val lastEntry = name
    val penultimateFile = parentFile ?: return this
    return if (penultimateFile.parent == null) {
        File(newName).resolve(lastEntry)
    } else {
        penultimateFile.parentFile.resolve(newName).resolve(lastEntry)
    }
}