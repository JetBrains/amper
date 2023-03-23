package org.jetbrains.deft.proto.frontend

import org.jetbrains.deft.proto.frontend.util.getPlatformFromFragmentName

internal typealias Settings = Map<String, Any>

internal inline fun <reified T : Any> Settings.getValue(key: String): T? = this[key] as? T

fun Settings.getSettings(key: String): Settings? = getValue<Settings>(key)
internal inline fun <reified T : Any> Settings.getByPath(vararg path: String): T? {
    var settings = this
    path.forEachIndexed { index, element ->
        val isLast = index == path.size - 1
        if (isLast) {
            return settings.getValue(element)
        }
        settings = settings.getSettings(element) ?: error("There is no such key '$element'")
    }

    return null
}

internal inline fun <reified T> Settings.handleFragmentSettings(
    fragments: List<MutableFragment>,
    key: String,
    init: MutableFragment.(T) -> Unit
) {
    val rawPlatforms = getByPath<List<String>>("product", "platforms") ?: listOf()
    val platforms = rawPlatforms.mapNotNull { getPlatformFromFragmentName(it) }
    // add external dependencies, compiler flags, etc
    val optionMap = buildMap {
        for (variant in variants) {
            for (option in (variant.getValue<List<Settings>>("options")
                ?: listOf()).mapNotNull { it.getValue<String>("name") }) {
                put("option", variant)
            }
        }
    }

    val variantSet = variants.toMutableSet()

    val defaultOptionMap = buildMap<Settings, String> {
        for (variant in variants) {
            val option = (variant.getValue<List<Settings>>("options")
                ?: listOf()).firstOrNull { it.getValue<Boolean>("default") ?: false }
                ?: error("Something went wrong")
            put(variant, option.getValue<String>("name") ?: error("Something went wrong"))
        }
    }

    for ((settingsKey, settingsValue) in filterKeys { it.startsWith(key) }) {
        val split = settingsKey.split(".")
        val specialization = if (split.size > 1) split[1].split("+") else listOf()
        val options = specialization.filter { getPlatformFromFragmentName(it) == null }.toSet()
        for (option in options) {
            val variant = optionMap[option] ?: error("There is no such variant option $option")
            variantSet.remove(variant)
        }

        val normalizedPlatforms =
            specialization.mapNotNull { getPlatformFromFragmentName(it) }.ifEmpty { platforms }.toSet()
        val normalizedOptions = options + variantSet.mapNotNull { defaultOptionMap[it] }

        val targetFragment = fragments
            .filter { it.platforms == normalizedPlatforms }
            .firstOrNull { it.variants == normalizedOptions }
            ?: error("Can't find a variant with platforms $normalizedPlatforms and variant options $normalizedOptions")

        targetFragment.init(settingsValue as T)
    }
}

internal val Settings.variants: List<Settings>
    get() {
        val initialVariants = getValue<List<Settings>>("variants") ?: listOf()
        return if (!initialVariants.any { it.getValue<String>("dimension") == "mode" }) {
            initialVariants + mapOf(
                "dimension" to "mode",
                "options" to listOf(
                    mapOf("name" to "main", "default" to true),
                    mapOf("name" to "test", "dependsOn" to listOf(mapOf("target" to "main")))
                )
            )
        } else {
            initialVariants
        }
    }