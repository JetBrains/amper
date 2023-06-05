@file:Suppress("UNCHECKED_CAST")

package org.jetbrains.deft.proto.frontend.fragments.yaml

import org.jetbrains.deft.proto.frontend.*
import org.jetbrains.deft.proto.frontend.util.getPlatformFromFragmentName
import org.yaml.snakeyaml.LoaderOptions
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.constructor.DuplicateKeyException
import java.nio.file.Path
import kotlin.io.path.name
import kotlin.io.path.readText

private val RESERVED_TOP_LEVEL_NAMES: Set<String> = setOf("type", "platforms", "variants")

internal fun parsePotato(potatoYaml: Path): PotatoModule =
    buildPotato(potatoYaml.readText(), potatoYaml.parent.name, PotatoModuleFileSource(potatoYaml))

internal fun parsePotato(text: String, potatoName: String): PotatoModule =
    buildPotato(text, potatoName, PotatoModuleProgrammaticSource)

private fun buildPotato(yaml: String, name: String, source: PotatoModuleSource): PotatoModule {
    val loaderOptions = LoaderOptions().apply { isAllowDuplicateKeys = false }
    val potatoMap = try {
        Yaml(loaderOptions).load<Map<String, Any>?>(yaml) ?: throw ParsingException("Got empty yaml")
    } catch (e: DuplicateKeyException) {
        throw ParsingException(e.message!!)
    }
    val type = parseType(potatoMap)
    val targetPlatforms = parseTargetPlatforms(potatoMap)
    val variants = parseVariants(potatoMap)
    validateVariants(variants)
    val explicitFragments = parseExplicitFragments(source, potatoMap)
    validateExplicitFragments(explicitFragments, variants)
    val (fragments, artifacts) = deduceFragments(name, explicitFragments, targetPlatforms, variants, type)

    return PotatoModuleImpl(
        name,
        type,
        source,
        fragments,
        artifacts,
        classBasedSet(),
    )
}

private fun validateVariants(variants: List<Variant>) {
    val uniqueNamesMap = mutableMapOf<String, Variant>()
    for (variant in variants) {
        for (variantValue in variant.values) {
            if ("+" in variantValue) throw ParsingException("'+' character can't be used in variants, but found in $variant")
            if (variantValue in uniqueNamesMap) throw ParsingException("Variant \"$variantValue\" is duplicated in variants $variant and ${uniqueNamesMap[variantValue]}")
            uniqueNamesMap[variantValue] = variant
        }
    }
}

private fun validateExplicitFragments(explicitFragments: Map<String, FragmentDefinition>, variants: List<Variant>) {
    for ((fragmentName, definition) in explicitFragments) {
        val fragmentParts = fragmentName.split("+")
        val fragmentBaseName = fragmentParts.first()
        val fragmentTrimmedName = fragmentBaseName.substringBeforeLast("Test")
        val possiblePlatform = getPlatformFromFragmentName(fragmentTrimmedName)
        if (possiblePlatform == null) {
            if (fragmentName == fragmentTrimmedName && definition.fragmentDependencies.isEmpty())
                throw ParsingException("User-defined fragment \"$fragmentBaseName\" isn't present in the natural hierarchy and should define its refines explicitly in \"refines\" block")
            if (fragmentTrimmedName !in explicitFragments)
                throw ParsingException("User-defined fragment \"$fragmentName\" doesn't have its base definition of \"$fragmentTrimmedName\" in \"fragments\" section")
        }

        val fragmentVariants = fragmentParts.drop(1)
        val usedFragments = mutableSetOf<Variant>()
        for (fragmentVariant in fragmentVariants) {
            val dimension = variants.find { fragmentVariant in it.values }
                ?: throw ParsingException("Fragment \"$fragmentName\" has unknown variant: $fragmentVariant")
            if (dimension in usedFragments) throw ParsingException("Fragment \"$fragmentName\" uses two or more variants from the same dimension \"$dimension\"")
            usedFragments.add(dimension)
        }
    }
}

private fun parseType(potatoMap: Map<String, Any>): PotatoModuleType = when (val rawType = potatoMap["type"]) {
    "lib" -> PotatoModuleType.LIBRARY
    "app" -> PotatoModuleType.APPLICATION
    else -> throw ParsingException("\"type\" field should be present and be one of [lib, app]. Actual: $rawType")
}

private fun parseTargetPlatforms(potatoMap: Map<String, Any>): Set<Platform> {
    val rawPlatforms = potatoMap["platforms"] as? List<String>
        ?: throw ParsingException("At least one platform should be provided in \"platforms\" fields")
    return rawPlatforms.map { rawPlatform ->
        val platform =
            getPlatformFromFragmentName(rawPlatform) ?: throw ParsingException("Unknown platform $rawPlatform")
        if (!platform.isLeaf) throw ParsingException("Intermediate platforms ($platform) can't be target")
        platform
    }.toSet()
}

private fun parseVariants(potatoMap: Map<String, Any>): List<Variant> {
    val rawVariants = potatoMap["variants"] as? List<List<String>> ?: return emptyList()
    return rawVariants.map(::Variant)
}

private fun parseExplicitFragments(
    source: PotatoModuleSource,
    potatoMap: Map<String, Any>
): Map<String, FragmentDefinition> {
    val rawFragments =
        potatoMap.filterKeys { it !in RESERVED_TOP_LEVEL_NAMES } as? Map<String, Map<String, Any>?> ?: return emptyMap()
    return rawFragments.map { (name, fragment) ->
        if (fragment == null) return@map name to FragmentDefinition(
            emptyList(),
            emptyList(),
            classBasedSet(),
            classBasedSet()
        )
        val externalDependencies = fragment["dependencies"] as? List<String> ?: emptyList()
        val refines = fragment["refines"]
        val fragmentDependencies = refines as? List<String> ?: (refines as? String)?.let(::listOf) ?: emptyList()
        val fragmentParts: ClassBasedSet<FragmentPart<*>> = buildClassBasedSet {
            val kotlinFragmentPart = (fragment["kotlin"] as Map<String, Any>?)?.let { parseKotlinFragmentPart(it) }
            if (kotlinFragmentPart != null) add(kotlinFragmentPart)
        }
        val artifactParts: ClassBasedSet<ArtifactPart<*>> = buildClassBasedSet {
            val jvmArtifactPart = parseJvmArtifactPart(fragment)
            if (jvmArtifactPart != null) add(jvmArtifactPart)
            val nativeArtifactPart = parseNativeArtifactPart(fragment)
            if (nativeArtifactPart != null) add(nativeArtifactPart)
            val androidArtifactPart = parseAndroidArtifactPart(fragment)
            if (androidArtifactPart != null) add(androidArtifactPart)
        }

        name to FragmentDefinition(
            externalDependencies.map { dependency ->
                if (dependency.startsWith(".")) {
                    check(source is PotatoModuleFileSource) { "Relative path syntax isn't applicable for programmatically created Pots" }
                    val targetPath = source.buildFile.parent.resolve(dependency).resolve("Pot.yaml").normalize()
                    InnerDependency(targetPath)
                } else {
                    MavenDependency(dependency)
                }
            },
            fragmentDependencies,
            fragmentParts,
            artifactParts,
        )
    }.toMap()
}

private fun parseNativeArtifactPart(fragment: Map<String, Any>): NativeApplicationArtifactPart? {
    val entryPoint = fragment["entryPoint"] as String? ?: return null
    return NativeApplicationArtifactPart(entryPoint)
}

private fun parseJvmArtifactPart(fragment: Map<String, Any>): JavaArtifactPart? {
    val mainClass = fragment["mainClass"] as String? ?: return null
    return JavaArtifactPart(mainClass, "", "17")
}

private fun parseAndroidArtifactPart(fragment: Map<String, Any>): AndroidArtifactPart? {
    val compileSdkVersion = fragment["compileSdkVersion"] as? String ?: return null
    val minSdkVersion = fragment["minSdkVersion"] as? Int
    val sourceCompatibility = fragment["sourceCompatibility"] as? String
    val targetCompatibility = fragment["targetCompatibility"] as? String
    return AndroidArtifactPart(compileSdkVersion, minSdkVersion, sourceCompatibility, targetCompatibility)
}

private fun parseKotlinFragmentPart(kotlinSettings: Map<String, Any>): KotlinFragmentPart {
    val languageVersion = (kotlinSettings["languageVersion"] as Double?)?.toString()
    val apiVersion = (kotlinSettings["apiVersion"] as Double?)?.toString()
    val progressiveMode = kotlinSettings["progressiveMode"] as Boolean?
    val languageFeatures = kotlinSettings["languageFeatures"] as List<String>? ?: emptyList()
    val optIns = kotlinSettings["optIns"] as List<String>? ?: emptyList()

    return KotlinFragmentPart(languageVersion, apiVersion, progressiveMode, languageFeatures, optIns)
}
