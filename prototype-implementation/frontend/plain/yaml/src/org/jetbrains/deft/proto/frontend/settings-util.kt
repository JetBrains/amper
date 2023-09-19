package org.jetbrains.deft.proto.frontend

import org.jetbrains.deft.proto.core.Result
import org.jetbrains.deft.proto.core.deftFailure
import org.jetbrains.deft.proto.core.messages.ProblemReporterContext
import org.jetbrains.deft.proto.frontend.nodes.YamlNode
import org.jetbrains.deft.proto.frontend.nodes.reportNodeError
import org.jetbrains.deft.proto.frontend.util.getPlatformFromFragmentName

context (BuildFileAware, ProblemReporterContext, ParsingContext)
internal inline fun <reified T : YamlNode> YamlNode.Mapping.handleFragmentSettings(
    fragments: List<FragmentBuilder>,
    key: String,
    init: FragmentBuilder.(T) -> Result<Unit>,
): Result<Unit> {
    val originalSettings = this
    var variantSet: MutableSet<Variant>

    val settings = this.mappings.map { (it.first as YamlNode.Scalar) to it.second }.filter { it.first.startsWith(key) }
    var hasErrors = false
    for ((settingsKey, settingsValue) in settings) {
        variantSet = variants.toMutableSet()
        val split = settingsKey.split("@")
        val specialization = if (split.size > 1) split[1].split("+") else listOf()
        val options = specialization
            .filter { getPlatformFromFragmentName(it) == null && !aliasMap.containsKey(it) }
            .toSet()

        for (option in options) {
            val variant = originalSettings.optionMap[option]
            if (variant == null) {
                problemReporter.reportNodeError(
                    FrontendYamlBundle.message(
                        "unknown.variant.option",
                        option,
                        originalSettings.optionMap.keys.filterNot { it.isSyntheticOption() }
                    ),
                    node = settingsKey,
                    file = buildFile,
                )
                hasErrors = true
                continue
            }
            variantSet.remove(variant)
        }

        val normalizedPlatforms = specialization
            .flatMap { aliasMap[it] ?: listOfNotNull(getPlatformFromFragmentName(it)) }
            .ifEmpty { platforms }
            .toSet()

        val normalizedOptions = options + variantSet.mapNotNull { defaultOptionMap[it] }

        val targetFragment = fragments
            .filter { it.platforms == normalizedPlatforms }
            .firstOrNull { it.variants == normalizedOptions }
        if (targetFragment == null) {
            problemReporter.reportNodeError(
                FrontendYamlBundle.message(
                    "cant.find.target.with.platforms.and.options",
                    normalizedPlatforms.map { it.pretty },
                    normalizedOptions.filterNot { it.isSyntheticOption() }
                ),
                node = settingsKey,
                file = buildFile,
            )
            hasErrors = true
            continue
        }

        if (settingsValue is T) {
            val result = targetFragment.init(settingsValue)
            if (result !is Result.Success) {
                hasErrors = true
            }
        }
    }

    return if (hasErrors) {
        deftFailure()
    } else {
        Result.success(Unit)
    }
}

context(ParsingContext)
internal val YamlNode.Mapping.defaultOptionMap: Map<Variant, String>
    get() = buildMap {
        for (variant in variants) {
            val option = variant.options.firstOrNull { it.isDefaultOption }
            checkNotNull(option) { "Each variant should have default options" }
            put(variant, option.name)
        }
    }

context(ParsingContext)
internal val YamlNode.Mapping.optionMap: Map<String, Variant>
    get() = buildMap {
        for (variant in variants) {
            for (option in variant.options) {
                put(option.name, variant)
            }
        }
    }

internal val YamlNode.Scalar.transformed: YamlNode.Scalar
    get() = copy(value = value.transformKey())

internal val YamlNode.Mapping.transformed: YamlNode.Mapping
    get() = copy(mappings = mappings.map { (key, value) -> (key as YamlNode.Scalar).transformed to value })
