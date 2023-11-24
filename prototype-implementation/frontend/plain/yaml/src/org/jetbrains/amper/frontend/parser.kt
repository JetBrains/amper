/*
 * Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend

import org.jetbrains.amper.core.Result
import org.jetbrains.amper.core.amperFailure
import org.jetbrains.amper.core.messages.ProblemReporterContext
import org.jetbrains.amper.core.system.DefaultSystemInfo
import org.jetbrains.amper.core.system.SystemInfo
import org.jetbrains.amper.frontend.model.PlainPotatoModule
import org.jetbrains.amper.frontend.nodes.*
import org.jetbrains.amper.frontend.util.getPlatformFromFragmentName
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name

class ParsingContext(val config: YamlNode.Mapping) {
    lateinit var aliasMap: Map<String, Set<Platform>>
    lateinit var platforms: Set<Platform>
    lateinit var variants: List<Variant>
}

context(BuildFileAware, ProblemReporterContext, ParsingContext)
fun parseModule(
    systemInfo: SystemInfo = DefaultSystemInfo,
): Result<PotatoModule> = with(ParsingContext(config)) parsingContext@{
    val productAndPlatforms = parseProductAndPlatforms(config)
    if (productAndPlatforms !is Result.Success) {
        return amperFailure()
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

    val aliases = config["aliases"] ?: YamlNode.Sequence.Empty
    if (!aliases.castOrReport<YamlNode.Sequence> { FrontendYamlBundle.message("element.name.aliases") }) {
        return amperFailure()
    }
    aliases.elements.forEach { alias ->
        if (!alias.castOrReport<YamlNode.Mapping> { FrontendYamlBundle.message("element.name.aliases") }) {
            return amperFailure()
        }
    }

    var hasBrokenAliases = false

    aliasMap = aliases.elements.map { alias ->
        if (!alias.castOrReport<YamlNode.Mapping> { FrontendYamlBundle.message("element.name.aliases") }) {
            return amperFailure()
        }

        alias.mappings.associate { (key, value) ->
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
        }
    }.fold(hashMapOf<String, Set<Platform>>()) { acc, map ->
        acc.putAll(map)
        acc
    } + naturalHierarchy
        .entries
        .sortedBy { it.value.size }
        .associate { (key, value) -> key to (platforms intersect value) }

    if (hasBrokenAliases) {
        return amperFailure()
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
        return amperFailure()
    }
    variants = configVariants.value

    fragments = fragments.multiplyFragments(variants)

    // TODO: Find out if these errors can be accumulated
    val dependenciesResult = fragments.handleExternalDependencies(config.transformed, systemInfo)
    if (dependenciesResult !is Result.Success) {
        return amperFailure()
    }
    val fragmentSettingsResult = fragments.handleSettings(config.transformed)
    if (fragmentSettingsResult !is Result.Success) {
        return amperFailure()
    }
    fragments.calculateSrcDir()

    val artifacts = fragments.artifacts(
        variants,
        productType,
        platforms
    )

    val artifactSettingsResult = artifacts.handleSettings(config.transformed, fragments)
    if (artifactSettingsResult !is Result.Success) {
        return amperFailure()
    }

    val partsResult = parseModuleParts(config)
    if (partsResult !is Result.Success) {
        return amperFailure()
    }

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
                partsResult.value,
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
        return amperFailure()
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
                return amperFailure()
            }
            productTypeNode = typeNode
            val platformNode = productValue["platforms"]
            if (!platformNode.castOrReport<YamlNode.Sequence?> { FrontendYamlBundle.message("element.name.platforms.list") }) {
                return amperFailure()
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
            return amperFailure()
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
        return amperFailure()
    }

    val actualPlatforms = if (productPlatformNode != null) {
        val platformsValue =
            productPlatformNode.getListOfElementsOrNull<YamlNode.Scalar> { FrontendYamlBundle.message("element.name.platforms.list") }
                ?: return amperFailure()

        if (platformsValue.isEmpty()) {
            problemReporter.reportNodeError(
                FrontendYamlBundle.message("product.platforms.should.not.be.empty"),
                node = productPlatformNode,
                file = buildFile,
            )
            return amperFailure()
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
            return amperFailure()
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
            return amperFailure()
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
            return amperFailure()
        }
        defaultPlatforms
    }

    return Result.success(actualType to actualPlatforms.toSet())
}
