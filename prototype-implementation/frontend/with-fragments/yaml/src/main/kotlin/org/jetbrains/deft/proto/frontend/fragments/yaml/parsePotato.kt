@file:Suppress("UNCHECKED_CAST")

package org.jetbrains.deft.proto.frontend.fragments.yaml

import org.jetbrains.deft.proto.frontend.*
import org.jetbrains.deft.proto.frontend.util.getPlatformFromFragmentName
import org.yaml.snakeyaml.Yaml
import java.nio.file.Path
import kotlin.io.path.name
import kotlin.io.path.readText

internal fun parsePotato(potatoYaml: Path): PotatoModule =
    buildPotato(potatoYaml.readText(), potatoYaml.parent.name, PotatoModuleFileSource(potatoYaml))

internal fun parsePotato(text: String, potatoName: String): PotatoModule =
    buildPotato(text, potatoName, PotatoModuleProgrammaticSource)

private fun buildPotato(yaml: String, name: String, source: PotatoModuleSource): PotatoModule {
    val potatoMap = Yaml().load<Map<String, Any>?>(yaml) ?: throw ParsingException("Got empty yaml")
    val type = parseType(potatoMap)
    val targetPlatforms = parseTargetPlatforms(potatoMap)
    val flavors = parseFlavors(potatoMap)
    val explicitFragments = parseExplicitFragments(potatoMap)
    val (fragments, artifacts) = deduceFragments(name, explicitFragments, targetPlatforms, flavors, type)

    return PotatoModuleImpl(
        name,
        type,
        source,
        fragments,
        artifacts,
    )
}

private fun parseType(potatoMap: Map<String, Any>): PotatoModuleType = when (val rawType = potatoMap["type"]) {
    "lib" -> PotatoModuleType.LIBRARY
    "app" -> PotatoModuleType.APPLICATION
    else -> throw ParsingException("\"type\" field should be present and be one of [lib, app]. Actual: $rawType")
}

private fun parseTargetPlatforms(potatoMap: Map<String, Any>): Set<Platform> {
    val rawPlatforms = potatoMap["platforms"] as? List<String> ?: throw ParsingException("At least one platform should be provided in \"platforms\" fields")
    return rawPlatforms.map { rawPlatform ->
        val platform = getPlatformFromFragmentName(rawPlatform) ?: throw ParsingException("Unknown platform $rawPlatform")
        if (!platform.isLeaf) throw ParsingException("Intermediate platforms ($platform) can't be target")
        platform
    }.toSet()
}

private fun parseFlavors(potatoMap: Map<String, Any>): List<Flavor> {
    val rawFlavors = (potatoMap["flavors"] ?: potatoMap["flavours"]) as? List<List<String>> ?: return emptyList()
    return rawFlavors.map(::Flavor)
}

private fun parseExplicitFragments(potatoMap: Map<String, Any>): Map<String, FragmentDefinition> {
    val rawFragments = potatoMap["fragments"] as? Map<String, Map<String, Any>> ?: return emptyMap()
    return rawFragments.map { (name, fragment) ->
        val externalDependencies = fragment["dependencies"] as? List<String> ?: emptyList()
        val fragmentDependencies = fragment["refines"] as? List<String> ?: emptyList()
        name to FragmentDefinition(
            externalDependencies.map { dependency ->
                if (dependency.startsWith(":")) {
                    InnerDependency(dependency.removePrefix(":"))
                } else {
                    MavenDependency(dependency)
                }
            },
            fragmentDependencies,
        )
    }.toMap()
}