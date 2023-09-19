package org.jetbrains.deft.proto.frontend

import org.jetbrains.deft.proto.core.Result
import org.jetbrains.deft.proto.core.deftFailure
import org.jetbrains.deft.proto.core.messages.ProblemReporterContext
import org.jetbrains.deft.proto.frontend.model.PlainPotatoModule
import org.jetbrains.deft.proto.frontend.nodes.*
import org.jetbrains.deft.proto.frontend.util.getPlatformFromFragmentName
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name

class ParsingContext(val config: YamlNode.Mapping) {
    lateinit var aliasMap: Map<String, Set<Platform>>
    lateinit var platforms: Set<Platform>
    lateinit var variants: List<Variant>
}

context(BuildFileAware)
internal fun parseError(message: CharSequence): Nothing {
    error("$buildFile: $message")
}

context(BuildFileAware, ProblemReporterContext, ParsingContext)
fun parseModule(
    osDetector: OsDetector = DefaultOsDetector(),
): Result<PotatoModule> = with(ParsingContext(config)) parsingContext@{
    val productAndPlatforms = parseProductAndPlatforms(config)
    if (productAndPlatforms !is Result.Success) {
        return deftFailure()
    }

    val productType = productAndPlatforms.value.first
    platforms = productAndPlatforms.value.second
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

    aliasMap = emptyMap()
    val naturalHierarchy = Platform.entries
        .filter { !it.isLeaf }
        .filter { it != Platform.COMMON }
        .associate { setOf(it).toCamelCaseString().first to it.leafChildren.toSet() }

    val aliases = config["aliases"] ?: YamlNode.Mapping.Empty
    if (!aliases.castOrReport<YamlNode.Mapping> { FrontendYamlBundle.message("element.name.aliases") }) {
        return deftFailure()
    }

    var hasBrokenAliases = false
    aliasMap = aliases.mappings.associate { (key, value) ->
        val name = (key as YamlNode.Scalar).value
        if (!value.castOrReport<YamlNode.Sequence> { FrontendYamlBundle.message("element.name.alias.platforms") }) {
            hasBrokenAliases = true
            return@associate name to emptySet()
        }
        val platformsList =
            value.getListOfElementsOrNull<YamlNode.Scalar> { FrontendYamlBundle.message("element.name.alias.platforms") }
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

    var fragments = with(this@parsingContext) { subsets.basicFragments }

    val configVariants = getVariants(config)
    if (configVariants !is Result.Success) {
        return deftFailure()
    }
    variants = configVariants.value

    fragments = fragments.multiplyFragments(variants)

    fragments.handleExternalDependencies(config.transformed, osDetector)
    fragments.handleSettings(config.transformed)
    fragments.calculateSrcDir()

    val artifacts = fragments.artifacts(
        variants,
        productType,
        platforms
    )

    artifacts.handleSettings(config.transformed, fragments)

    val mutableState = object : Stateful<FragmentBuilder, Fragment> {
        private val mutableState = mutableMapOf<FragmentBuilder, Fragment>()
        override val state: MutableMap<FragmentBuilder, Fragment>
            get() = mutableState
    }
    return Result.success(with(mutableState) {
        with(variants) {
            PlainPotatoModule(
                productType,
                fragments,
                artifacts,
                parseModuleParts(config),
            )
        }
    })
}

context(BuildFileAware, ProblemReporterContext, ParsingContext)
internal fun parseProductAndPlatforms(config: YamlNode.Mapping): Result<Pair<ProductType, Set<Platform>>> {
    val productValue = config["product"]
    if (productValue == null) {
        problemReporter.reportNodeError(
            FrontendYamlBundle.message("product.field.is.missing"),
            node = config,
            file = buildFile,
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
            problemReporter.reportNodeError(
                FrontendYamlBundle.message(
                    "wrong.product.field.format",
                    productValue.nodeType,
                    ProductType.entries,
                ),
                node = productValue,
                file = buildFile,
            )
            return deftFailure()
        }
    }

    val actualType = ProductType.findForValue(productTypeNode.value)
    if (actualType == null) {
        problemReporter.reportNodeError(
            FrontendYamlBundle.message(
                "wrong.product.type",
                productTypeNode.value,
                ProductType.entries,
            ),
            node = productTypeNode,
            file = buildFile,
        )
        return deftFailure()
    }

    val actualPlatforms = if (productPlatformNode != null) {
        val platformsValue =
            productPlatformNode.getListOfElementsOrNull<YamlNode.Scalar> { FrontendYamlBundle.message("element.name.platforms.list") }
                ?: return deftFailure()

        if (platformsValue.isEmpty()) {
            problemReporter.reportNodeError(
                FrontendYamlBundle.message("product.platforms.should.not.be.empty"),
                node = productPlatformNode,
                file = buildFile,
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
                problemReporter.reportNodeError(
                    FrontendYamlBundle.message(
                        "product.unknown.platform",
                        platform.value,
                    ),
                    node = platform,
                    file = buildFile,
                )
            }
            return deftFailure()
        }

        val unsupportedPlatforms = knownPlatforms.keys.subtract(actualType.supportedPlatforms)
        if (unsupportedPlatforms.isNotEmpty()) {
            unsupportedPlatforms.forEach { platform ->
                problemReporter.reportNodeError(
                    FrontendYamlBundle.message(
                        "product.unsupported.platform",
                        actualType,
                        platform.pretty,
                        actualType.supportedPlatforms.map { it.pretty },
                    ),
                    node = knownPlatforms[platform],
                    file = buildFile,
                )
            }
            return deftFailure()
        }

        knownPlatforms.keys
    } else {
        val defaultPlatforms = actualType.defaultPlatforms
        if (defaultPlatforms == null) {
            problemReporter.reportNodeError(
                FrontendYamlBundle.message("product.type.does.not.have.default.platforms", actualType),
                node = productTypeNode,
                file = buildFile,
            )
            return deftFailure()
        }
        defaultPlatforms
    }

    return Result.success(actualType to actualPlatforms.toSet())
}
