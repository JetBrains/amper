package org.jetbrains.deft.proto.frontend

import org.jetbrains.deft.proto.core.Result
import org.jetbrains.deft.proto.core.deftFailure
import org.jetbrains.deft.proto.core.messages.ProblemReporterContext
import org.jetbrains.deft.proto.frontend.model.PlainPotatoModule
import org.jetbrains.deft.proto.frontend.nodes.*
import org.jetbrains.deft.proto.frontend.util.getPlatformFromFragmentName
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name

context(BuildFileAware)
internal fun parseError(message: CharSequence): Nothing {
    error("$buildFile: $message")
}

context(BuildFileAware, ProblemReporterContext)
fun parseModule(config: YamlNode.Mapping, osDetector: OsDetector = DefaultOsDetector()): Result<PotatoModule> {
    val productAndPlatforms = parseProductAndPlatforms(config)
    if (productAndPlatforms !is Result.Success) {
        return deftFailure()
    }

    val (productType: ProductType, platforms: Set<Platform>) = productAndPlatforms.value

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

    val aliases = config["aliases"] ?: YamlNode.Mapping.Empty
    if (!aliases.castOrReport<YamlNode.Mapping> { FrontendYamlBundle.message("element.name.aliases") }) {
        return deftFailure()
    }

    var hasBrokenAliases = false
    val aliasMap: Map<String, Set<Platform>> = aliases.mappings.associate { (key, value) ->
        val name = (key as YamlNode.Scalar).value
        if (!value.castOrReport<YamlNode.Sequence> { FrontendYamlBundle.message("element.name.alias.platforms") }) {
            hasBrokenAliases = true
            return@associate name to emptySet()
        }
        val platformsList = value.getListOfElementsOrNull<YamlNode.Scalar> { FrontendYamlBundle.message("element.name.alias.platforms") }
        if (platformsList == null) {
            hasBrokenAliases = true
            return@associate name to emptySet()
        }

        val platformSet = platformsList
            .mapNotNull { getPlatformFromFragmentName(it.value) }
            .toSet()
        name to platformSet
    } + naturalHierarchy
        .entries
        .sortedBy { it.value.size }
        .associate { (key, value) -> key to (platforms intersect value) }

    if (hasBrokenAliases) {
        return deftFailure()
    }

    val aliasSubsets = aliasMap
        .values
        .filter { platformSet -> platformSet.isNotEmpty() }
        .map { platformSet -> platformSet.map { it.pretty } }
        .distinct()
        .toSet()

    val subsets = (dependencySubsets + folderSubsets + aliasSubsets)
        .map { platformSet ->
            platformSet.flatMap {
                aliasMap[it] ?: listOfNotNull(getPlatformFromFragmentName(it))
            }.filter { it != Platform.COMMON }.toSet()
        }
        .filter { it.isNotEmpty() }
        .toSet() + platforms.map { setOf(it) }

    var fragments = with(aliasMap) { subsets.basicFragments }

    val config = config.toSettings()
    fragments = fragments.multiplyFragments(config.variants)
    with(aliasMap) {
        fragments.handleExternalDependencies(config.transformed, osDetector)
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
    return Result.success(with(mutableState) {
        with(config.variants.typeSafe) {
            PlainPotatoModule(
                productType,
                fragments,
                artifacts,
                parseModuleParts(config),
            )
        }
    })
}

context(BuildFileAware, ProblemReporterContext)
internal fun parseProductAndPlatforms(config: YamlNode.Mapping): Result<Pair<ProductType, Set<Platform>>> {
    val productValue = config["product"]
    if (productValue == null) {
        problemReporter.reportError(
            FrontendYamlBundle.message("product.field.is.missing"),
            file = buildFile,
            line = config.startMark.line + 1
        )
        return deftFailure()
    }

    val productTypeNode: YamlNode.Scalar
    val productPlatformNode: YamlNode.Sequence?

    when (productValue) {
        is YamlNode.Scalar -> {
            productTypeNode = productValue
            productPlatformNode = null
        }

        is YamlNode.Mapping -> {
            val typeNode = productValue["type"]
            if (!typeNode.castOrReport<YamlNode.Scalar> { FrontendYamlBundle.message("element.name.product.type") }) {
                return deftFailure()
            }
            productTypeNode = typeNode
            val platformNode = productValue["platforms"]
            if (!platformNode.castOrReport<YamlNode.Sequence?> { FrontendYamlBundle.message("element.name.platforms.list") }) {
                return deftFailure()
            }
            productPlatformNode = platformNode
        }

        else -> {
            problemReporter.reportError(
                FrontendYamlBundle.message(
                    "wrong.product.field.format",
                    productValue.nodeType,
                    ProductType.entries,
                ),
                file = buildFile,
                line = productValue.startMark.line + 1,
            )
            return deftFailure()
        }
    }

    val actualType = ProductType.findForValue(productTypeNode.value)
    if (actualType == null) {
        problemReporter.reportError(
            FrontendYamlBundle.message(
                "wrong.product.type",
                productTypeNode.value,
                ProductType.entries,
            ),
            file = buildFile,
            line = productTypeNode.startMark.line + 1,
        )
        return deftFailure()
    }

    val actualPlatforms = if (productPlatformNode != null) {
        val platformsValue =
            productPlatformNode.getListOfElementsOrNull<YamlNode.Scalar> { FrontendYamlBundle.message("element.name.platforms.list") }
                ?: return deftFailure()

        if (platformsValue.isEmpty()) {
            problemReporter.reportError(
                FrontendYamlBundle.message("product.platforms.should.not.be.empty"),
                file = buildFile,
                line = productPlatformNode.startMark.line + 1
            )
            return deftFailure()
        }

        val knownPlatforms = mutableMapOf<Platform, YamlNode>()
        val unknownPlatforms = mutableSetOf<YamlNode.Scalar>()
        platformsValue.forEach {
            val mapped = getPlatformFromFragmentName(it.value)
            if (mapped != null) {
                knownPlatforms[mapped] = it
            } else {
                unknownPlatforms.add(it)
            }
        }

        if (unknownPlatforms.isNotEmpty()) {
            unknownPlatforms.forEach { platform ->
                problemReporter.reportError(
                    FrontendYamlBundle.message(
                        "product.unknown.platform",
                        platform.value,
                    ),
                    file = buildFile,
                    line = platform.startMark.line + 1,
                )
            }
            return deftFailure()
        }

        val unsupportedPlatforms = knownPlatforms.keys.subtract(actualType.supportedPlatforms)
        if (unsupportedPlatforms.isNotEmpty()) {
            unsupportedPlatforms.forEach { platform ->
                problemReporter.reportError(
                    FrontendYamlBundle.message(
                        "product.unsupported.platform",
                        actualType,
                        platform.pretty,
                        actualType.supportedPlatforms.map { it.pretty },
                    ),
                    file = buildFile,
                    line = (knownPlatforms[platform]?.startMark?.line ?: 0) + 1,
                )
            }
            return deftFailure()
        }

        knownPlatforms.keys
    } else {
        val defaultPlatforms = actualType.defaultPlatforms
        if (defaultPlatforms == null) {
            problemReporter.reportError(
                FrontendYamlBundle.message("product.type.does.not.have.default.platforms", actualType),
                file = buildFile,
                line = productTypeNode.startMark.line + 1,
            )
            return deftFailure()
        }
        defaultPlatforms
    }

    return Result.success(actualType to actualPlatforms.toSet())
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
        if (unsupportedPlatforms.isNotEmpty()) reportUnsupportedPlatforms(unsupportedPlatforms.map { it.pretty })

        knownPlatforms
    } else {
        actualType.defaultPlatforms
            ?: parseError("product:platforms: should not be empty for '$actualType' product type")
    }

    return Pair(actualType, actualPlatforms.toSet())
}


