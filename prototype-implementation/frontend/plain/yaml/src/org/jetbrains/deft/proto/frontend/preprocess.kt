package org.jetbrains.deft.proto.frontend

import org.yaml.snakeyaml.Yaml
import java.io.InputStream

context(InterpolateCtx)
fun Yaml.parseAndPreprocess(
    inputStream: InputStream,
    loader: (String) -> InputStream,
): Settings {
    val rootConfig = load<Settings>(inputStream)
    val includedConfigNames = rootConfig.getValue<List<String>>("apply") ?: emptyList()
    // Remove this when we will move to new version of deft.
    val includedConfigNamesDeprecated = rootConfig.getValue<List<String>>("include") ?: emptyList()
    val allInclude = includedConfigNames + includedConfigNamesDeprecated
    val includedConfigs = allInclude.map(loader).map { load<Settings>(it) }
    val resultConfig = includedConfigs.fold(rootConfig) { acc, from -> merge(from, acc) }
    return resultConfig.doInterpolate()
}

/**
 * Simple merge algorithm that do not handle lists at all and just overrides
 * key/value pairs.
 */
private fun merge(
    to: Settings,
    from: Settings,
    previousPath: String = "",
    ignoreToKeys: Collection<String> = setOf("apply", "include")
): Settings = buildMap {
    val allKeys = to.keys + from.keys

    // Pass all "from" keys, that are ignored on "to" side.
    ignoreToKeys.forEach { key ->
        from[key]?.let { put(key, it) }
    }

    // Merge all other keys.
    allKeys.filter { it !in ignoreToKeys }.forEach { key ->
        val nextPath = "$previousPath.$key"
        val toValue = to[key]
        val fromValue = from[key]
        if (toValue != null && fromValue == null) put(key, toValue)
        else if (fromValue != null && toValue == null) put(key, fromValue)
        // Need or condition to enable smart casts.
        else if (toValue == null || fromValue == null) return@forEach
        else {
            when {
                toValue is Map<*, *> && fromValue is Map<*, *> ->
                    put(
                        key,
                        merge(toValue as Settings, fromValue as Settings, nextPath)
                    )

                toValue is List<*> && fromValue is List<*> ->
                    put(
                        key,
                        toValue + fromValue
                    )

                toValue::class == fromValue::class ->
                    put(key, fromValue)

                else -> {
                    error(
                        "Error while merging two configs: " +
                                "Values under path $nextPath have different types." +
                                "First config type: ${toValue::class.simpleName}. " +
                                "Second config type: ${fromValue::class.simpleName}." +
                                "(Maybe type is a container type and there is an inner type conflict)"
                    )
                }
            }
        }
    }
}

context(InterpolateCtx)
internal fun Settings.doInterpolate(): Settings =
    transformLeafs { leaf ->
        if (leaf !is String) leaf
        else leaf.tryInterpolate()
    }

internal fun Settings.transformLeafs(transform: (Any) -> Any): Settings =
    buildMap {
        this@transformLeafs.entries.map { entry ->
            val value = entry.value
            val key = entry.key
            @Suppress("SENSELESS_COMPARISON")
            if (value != null) {
                when (value) {
                    is List<*> -> {
                        // The list contains other objects.
                        if (value.isNotEmpty() && value.first() is Map<*, *>)
                            put(key, value.map { (it as Settings).transformLeafs(transform) })
                        // The list contains only leaf elements or is empty.
                        else
                            put(key, value.map { it?.let(transform) })
                    }

                    is Map<*, *> -> put(key, (value as Settings).transformLeafs(transform))
                    else -> put(key, transform(value))
                }
            }

        }
    }
