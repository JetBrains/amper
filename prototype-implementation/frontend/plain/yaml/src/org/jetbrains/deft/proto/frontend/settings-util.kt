package org.jetbrains.deft.proto.frontend

import org.jetbrains.deft.proto.core.messages.ProblemReporterContext
import org.jetbrains.deft.proto.frontend.nodes.YamlNode
import org.jetbrains.deft.proto.frontend.util.getPlatformFromFragmentName

context (BuildFileAware, ProblemReporterContext, ParsingContext)
internal inline fun <reified T : YamlNode> YamlNode.Mapping.handleFragmentSettings(
    fragments: List<FragmentBuilder>,
    key: String,
    init: FragmentBuilder.(T) -> Unit,
) {
    val originalSettings = this
    var variantSet: MutableSet<Variant>

    val settings = this.mappings.map { (it.first as YamlNode.Scalar) to it.second }.filter { it.first.startsWith(key) }
    for ((settingsKey, settingsValue) in settings) {
        variantSet = variants.toMutableSet()
        val split = settingsKey.split("@")
        val specialization = if (split.size > 1) split[1].split("+") else listOf()
        val options = specialization
            .filter { getPlatformFromFragmentName(it) == null && !aliasMap.containsKey(it) }
            .toSet()

        for (option in options) {
            val variant = originalSettings.optionMap[option] ?: parseError("There is no such variant option $option")
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
            ?: parseError("Can't find a variant with platforms $normalizedPlatforms and variant options $normalizedOptions")

        if (settingsValue is T) {
            targetFragment.init(settingsValue)
        }
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
