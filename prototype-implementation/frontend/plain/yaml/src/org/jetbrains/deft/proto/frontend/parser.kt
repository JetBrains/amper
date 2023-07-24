package org.jetbrains.deft.proto.frontend

import org.jetbrains.deft.proto.frontend.model.PlainPotatoModule
import org.jetbrains.deft.proto.frontend.util.getPlatformFromFragmentName
import java.nio.file.Path
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name

context(BuildFileAware)
internal fun parseError(message: CharSequence): Nothing {
    error("$buildFile: $message")
}

context(BuildFileAware)
fun parseModule(config: Settings): PotatoModule {
    val (productType: ProductType, platforms: Set<Platform>) = parseProductAndPlatforms(config)

    val dependencySubsets = config.keys
        .asSequence()
        .map { it.split("@") }
        .filter { it.size > 1 }
        .map { it[1] }
        .map { it.split("+").toSet() }
        .toSet()

    val folderSubsets = buildFile.parent
        .listDirectoryEntries()
        .map { it.name }
        .map { it.split("+").toSet() }
        .toSet()

    val naturalHierarchy = Platform.entries
        .filter { !it.isLeaf }
        .filter { it != Platform.COMMON }
        .associate { with(mapOf<String, Set<Platform>>()) { setOf(it).toCamelCaseString().first } to it.leafChildren.toSet() }

    val aliases: Settings = config.getValue<Settings>("aliases") ?: mapOf()
    val aliasMap: Map<String, Set<Platform>> = aliases.entries.associate {
        val name = it.key
        val platformSet = aliases.getValue<List<String>>(it.key)
            ?.mapNotNull { getPlatformFromFragmentName(it) }
            ?.toSet() ?: setOf()
        name to platformSet
    } + naturalHierarchy
        .entries
        .sortedBy { it.value.size }
        .associate { (key, value) -> key to (platforms intersect value) }

    val subsets = (dependencySubsets + folderSubsets)
        .map {
            it.flatMap {
                aliasMap[it] ?: listOfNotNull(getPlatformFromFragmentName(it))
            }.filter { it != Platform.COMMON }.toSet()
        }
        .filter { it.isNotEmpty() }
        .toSet() + platforms.map { setOf(it) }

    var fragments = with(aliasMap) { subsets.basicFragments }

    fragments = fragments.multiplyFragments(config.variants)
    with(aliasMap) {
        fragments.handleExternalDependencies(config.transformed)
        fragments.handleSettings(config.transformed)
    }
    with(config) {
        fragments.calculateSrcDir(platforms)
    }

    val artifacts = fragments.artifacts(
        config.variants,
        productType,
        platforms
    )

    with(aliasMap) {
        artifacts.handleSettings(config.transformed, fragments)
    }

    val mutableState = object : Stateful<FragmentBuilder, Fragment> {
        private val mutableState = mutableMapOf<FragmentBuilder, Fragment>()
        override val state: MutableMap<FragmentBuilder, Fragment>
            get() = mutableState
    }
    return with(mutableState) {
        PlainPotatoModule(
            productType, 
            fragments,
            artifacts,
            parseModuleParts(config),
        )
    }
}

context(BuildFileAware)
internal fun parseProductAndPlatforms(config: Settings): Pair<ProductType, Set<Platform>> {
    val productValue = config.getValue<Any>("product") ?: parseError("product: section is missing")
    val typeValue: String
    val platformsValue: List<String>?

    fun unsupportedType(userValue: Any): Nothing {
        parseError(
            "unsupported product type '$userValue', supported types:\n"
                    + ProductType.entries.joinToString("\n")
        )
    }

    when (productValue) {
        is String -> {
            typeValue = productValue
            platformsValue = null
        }

        is Map<*, *> -> {
            @Suppress("UNCHECKED_CAST")
            productValue as Settings

            typeValue = productValue.getStringValue("type") ?: parseError("product:type: is missing")
            platformsValue = productValue.getValue<List<String>>("platforms")
        }

        else -> {
            unsupportedType(productValue)
        }
    }

    val actualType = ProductType.findForValue(typeValue) ?: unsupportedType(typeValue)

    val actualPlatforms = if (platformsValue != null) {
        if (platformsValue.isEmpty()) {
            parseError("product:platforms: should not be empty")
        }

        val knownPlatforms = mutableSetOf<Platform>()
        val unknownPlatforms = mutableSetOf<String>()
        platformsValue.forEach {
            val mapped = getPlatformFromFragmentName(it)
            if (mapped != null) {
                knownPlatforms.add(mapped)
            } else {
                unknownPlatforms.add(it)
            }
        }

        fun reportUnsupportedPlatforms(toReport: Collection<String>) {
            val message = StringBuilder("product type '$actualType' doesn't support ")
            toReport.joinTo(message) { "'$it'" }
            message.append(if (toReport.size == 1) " platform" else " platforms")
            parseError(message)
        }
        if (unknownPlatforms.isNotEmpty()) reportUnsupportedPlatforms(unknownPlatforms)

        val unsupportedPlatforms = knownPlatforms.subtract(actualType.supportedPlatforms)
        if (unsupportedPlatforms.isNotEmpty()) reportUnsupportedPlatforms(unsupportedPlatforms.map { it.pretty} )

        knownPlatforms
    } else {
        actualType.defaultPlatforms
            ?: parseError("product:platforms: should not be empty for '$actualType' product type")
    }

    return Pair(actualType, actualPlatforms.toSet())
}


