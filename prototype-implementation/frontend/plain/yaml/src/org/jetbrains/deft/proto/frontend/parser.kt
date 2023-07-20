package org.jetbrains.deft.proto.frontend

import org.jetbrains.deft.proto.frontend.model.PlainPotatoModule
import org.jetbrains.deft.proto.frontend.util.getPlatformFromFragmentName
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name

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

    val naturalHierarchy = Platform.values()
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

internal fun parseProductAndPlatforms(config: Settings): Pair<ProductType, Set<Platform>> {
    val productValue = config.getValue<Any>("product") ?: error("product: section is missing")
    val typeValue: String
    val platformsValue: List<String>?

    fun unsupportedType(userValue: Any): Nothing {
        error(
            "unsupported product type: $userValue, supported types:\n"
                    + Platform.entries.joinToString("\n")
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

            typeValue = productValue.getStringValue("type") ?: error("product:type: is missing")
            platformsValue = productValue.getValue<List<String>>("platforms")
        }

        else -> {
            unsupportedType(productValue)
        }
    }

    val actualType = ProductType.findForValue(typeValue) ?: unsupportedType(typeValue)

    val actualPlatforms = if (platformsValue != null) {
        if (platformsValue.isEmpty()) {
            error("product:platforms: should not be empty")
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

        fun reportUnsupportedPlatforms(toReport: Set<Any>) {
            val message = StringBuilder("product type $actualType doesn't support ")
            toReport.joinTo(message) { "'$it'" }
            message.append(if (unknownPlatforms.size == 1) " platform" else " platforms")
            error(message)
        }
        if (unknownPlatforms.isNotEmpty()) reportUnsupportedPlatforms(unknownPlatforms)

        val unsupportedPlatforms = knownPlatforms.subtract(actualType.supportedPlatforms)
        if (unsupportedPlatforms.isNotEmpty()) reportUnsupportedPlatforms(unsupportedPlatforms)

        knownPlatforms
    } else {
        actualType.defaultPlatforms
            ?: error("product:platforms: should not be empty for product type $actualType")
    }

    return Pair(actualType, actualPlatforms.toSet())
}


