package org.jetbrains.deft.proto.frontend

import org.jetbrains.deft.proto.frontend.util.getPlatformFromFragmentName

internal typealias Settings = Map<String, Any>

internal inline fun <reified T : Any> Settings.getValue(key: String): T? = this[key] as? T

internal inline fun <reified T : Any> Settings.requireValue(
    key: String, handler: () -> String
): T = this[key] as? T ?: error(handler())

fun Settings.getSettings(key: String): Settings? = getValue<Settings>(key)
internal inline fun <reified T : Any> Settings.getByPath(vararg path: String): T? {
    var settings = this
    path.forEachIndexed { index, element ->
        val isLast = index == path.size - 1
        if (isLast) {
            return settings.getValue(element)
        }
        settings = settings.getSettings(element) ?: return null
    }

    return null
}

context (Map<String, Set<Platform>>)
internal inline fun <reified T : Any> Settings.handleFragmentSettings(
    fragments: List<FragmentBuilder>,
    key: String,
    init: FragmentBuilder.(T) -> Unit
) {
    val originalSettings = this
    val rawPlatforms = getByPath<List<String>>("product", "platforms") ?: listOf()
    val platforms = rawPlatforms.mapNotNull { getPlatformFromFragmentName(it) }
    var variantSet: MutableSet<Settings>

    for ((settingsKey, settingsValue) in filterKeys { it.startsWith(key) }) {
        variantSet = with(originalSettings) { variants }.toMutableSet()
        val split = settingsKey.split("@")
        val specialization = if (split.size > 1) split[1].split("+") else listOf()
        val options = specialization
            .filter { getPlatformFromFragmentName(it) == null && !this@Map.containsKey(it) }
            .toSet()

        for (option in options) {
            val variant = originalSettings.optionMap[option] ?: error("There is no such variant option $option")
            variantSet.remove(variant)
        }

        val normalizedPlatforms = specialization
            .flatMap { this@Map[it] ?: listOfNotNull(getPlatformFromFragmentName(it)) }
            .ifEmpty { platforms }
            .toSet()

        val normalizedOptions = options + variantSet.mapNotNull { defaultOptionMap[it] }

        val targetFragment = fragments
            .filter { it.platforms == normalizedPlatforms }
            .firstOrNull { it.variants == normalizedOptions }
            ?: error("Can't find a variant with platforms $normalizedPlatforms and variant options $normalizedOptions")

        if (settingsValue is T) {
            targetFragment.init(settingsValue)
        }
    }
}

context (Map<String, Set<Platform>>)
internal inline fun <reified T : Any> Settings.handleArtifactSettings(
    artifacts: List<ArtifactBuilder>,
    fragments: List<FragmentBuilder>,
    key: String,
    init: ArtifactBuilder.(T) -> Unit
) {
    val originalSettings = this
    val rawPlatforms = getByPath<List<String>>("product", "platforms") ?: listOf()
    val platforms = rawPlatforms.mapNotNull { getPlatformFromFragmentName(it) }
    var variantSet: MutableSet<Settings>

    for ((settingsKey, settingsValue) in filterKeys { it.startsWith(key) }) {
        variantSet = with(originalSettings) { variants }.toMutableSet()
        val split = settingsKey.split("@")
        val specialization = if (split.size > 1) split[1].split("+") else listOf()
        val options = specialization
            .filter { getPlatformFromFragmentName(it) == null && !this@Map.containsKey(it) }
            .toSet()

        for (option in options) {
            val variant = originalSettings.optionMap[option] ?: error("There is no such variant option $option")
            variantSet.remove(variant)
        }

        val normalizedPlatforms = specialization
            .flatMap { this@Map[it] ?: listOfNotNull(getPlatformFromFragmentName(it)) }
            .ifEmpty { platforms }
            .toSet()

        val normalizedOptions = options + variantSet.mapNotNull { defaultOptionMap[it] }

        val targetFragment = fragments
            .filter { it.platforms == normalizedPlatforms }
            .firstOrNull { it.variants == normalizedOptions }
            ?: error("Can't find a variant with platforms $normalizedPlatforms and variant options $normalizedOptions")


        artifacts.filter { it.fragments.withDependencies().contains(targetFragment) }.forEach {
            if (settingsValue is T) {
                it.init(settingsValue)
            }
        }
    }
}

private fun MutableList<FragmentBuilder>.withDependencies(): MutableList<FragmentBuilder> = buildSet {
    val deque = ArrayDeque<FragmentBuilder>()
    this@withDependencies.firstOrNull()?.let {
        deque.add(it)
        add(it)
    }
    while (!deque.isEmpty()) {
        val fragment = deque.removeFirst()
        for (dep in fragment.dependencies.map { it.target }) {
            if (!contains(dep)) {
                deque.add(dep)
                add(dep)
            }
        }

    }
}.toMutableList()

internal val Settings.variants: List<Settings>
    get() {
        val initialVariants = (this["variants"] as? List<*>)?.let {
            if (it.isNotEmpty()) {
                if (it[0] is String) {
                    listOf(getValue<List<String>>("variants") ?: listOf())
                } else {
                    getValue<List<List<String>>>("variants") ?: listOf()
                }
            } else {
                listOf()
            }
        } ?: listOf()

        var i = 0
        val convertedInitialVariants: List<Settings> = initialVariants.map {
            val dimension = "dimension${++i}"
            mapOf(
                "dimension" to dimension,
                "options" to it.map { optionName ->
                    mapOf(
                        "name" to optionName, "dependsOn" to listOf(
                            mapOf("target" to dimension)
                        )
                    )
                } + mapOf("name" to dimension, "default" to true)
            )
        }
        return if (!convertedInitialVariants.any { it.getValue<String>("dimension") == "mode" }) {
            convertedInitialVariants + mapOf(
                "dimension" to "mode",
                "options" to listOf(
                    mapOf("name" to "main", "default" to true),
                    mapOf("name" to "test", "dependsOn" to listOf(mapOf("target" to "main", "kind" to "friend")))
                )
            )
        } else {
            convertedInitialVariants
        }
    }

internal val Settings.defaultOptionMap: Map<Settings, String>
    get() {
        val originalSettings = this

        return buildMap {
            for (variant in with(originalSettings) { variants }) {
                val option = (variant.getValue<List<Settings>>("options")
                    ?: listOf()).firstOrNull { it.getValue<Boolean>("default") ?: false }
                    ?: error("Something went wrong")
                put(variant, option.getValue<String>("name") ?: error("Something went wrong"))
            }
        }
    }

internal val Settings.optionMap: Map<String, Settings>
    get() {
        val originalSettings = this

        return buildMap {
            for (variant in with(originalSettings) { variants }) {
                for (option in (variant.getValue<List<Settings>>("options")
                    ?: listOf()).mapNotNull { it.getValue<String>("name") }) {
                    put(option, variant)
                }
            }
        }
    }

internal val Settings.transformed: Settings
    get() = buildMap {
        for ((key, value) in this@transformed) {
            put(key.transformKey(), value)
        }
    }