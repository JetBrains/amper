package org.jetbrains.deft.proto.frontend

import org.jetbrains.deft.proto.frontend.util.getPlatformFromFragmentName

internal typealias Settings = Map<String, Any>

// Simplification of map access.
open class SettingsKey<T : Any>(val name: String)
open class DefaultedKey<T : Any>(name: String, val default: T) : SettingsKey<T>(name)

internal inline operator fun <reified T : Any> Settings.get(key: SettingsKey<T>): T? =
    this[key.name] as? T

internal inline operator fun <reified T : Any> Settings.get(key: DefaultedKey<T>): T =
    this[key.name] as? T ?: key.default


internal inline fun <reified T : Any> Settings.getValue(key: String): T? = this[key] as? T

internal inline fun <reified T : Any> Settings.getValue(
    key: String, block: (T) -> Unit
) {
    (this[key] as? T)?.let(block)
}

internal fun Settings.getStringValue(key: String) = this[key]?.toString()

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
    val (_, platforms) = parseProductAndPlatforms(originalSettings)
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
    fragments: List<FragmentBuilder>,
    key: String,
    init: FragmentBuilder.(T) -> Unit
) {
    val originalSettings = this
    val (_, platforms) = parseProductAndPlatforms(originalSettings)
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

        if (settingsValue is T) targetFragment.init(settingsValue)
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

/**
 * Key used to propagate naming correctly for fragments while multiplying.
 */
val isDefaultKey = DefaultedKey("default", false)
/**
 * Key used to determine, which fragment is built by default.
 */
val isDefaultFragmentKey = DefaultedKey("defaultFragment", false)
val dependsOnKey = DefaultedKey<List<Settings>>("dependsOn", emptyList())
val optionsKey = DefaultedKey<List<Settings>>("options", emptyList())
val nameKey = SettingsKey<String>("name")

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
                optionsKey.name to it.mapIndexed { index, optionName ->
                    mapOf(
                        nameKey.name to optionName,
                        dependsOnKey.name to listOf(
                            mapOf("target" to dimension)
                        ),
                        isDefaultFragmentKey.name to (index == 0),
                    )
                } + mapOf(
                    nameKey.name to dimension,
                    isDefaultKey.name to true
                )
            )
        }
        return if (!convertedInitialVariants.any { it.getStringValue("dimension") == "mode" }) {
            convertedInitialVariants + mapOf(
                "dimension" to "mode",
                optionsKey.name to listOf(
                    mapOf(
                        nameKey.name to "main",
                        isDefaultKey.name to true,
                        isDefaultFragmentKey.name to true
                    ),
                    mapOf(
                        nameKey.name to "test",
                        dependsOnKey.name to listOf(mapOf("target" to "main", "kind" to "friend"))
                    )
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
                val option = (variant[optionsKey])
                    .firstOrNull { it[isDefaultKey] }
                    ?: error("Something went wrong")
                put(variant, option.getStringValue("name") ?: error("Something went wrong"))
            }
        }
    }

internal val Settings.optionMap: Map<String, Settings>
    get() {
        val originalSettings = this

        return buildMap {
            for (variant in with(originalSettings) { variants }) {
                for (option in (variant[optionsKey])
                    .mapNotNull { it.getStringValue("name") }) {
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