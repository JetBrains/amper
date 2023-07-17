package org.jetbrains.deft.proto.frontend

import org.yaml.snakeyaml.Yaml
import java.nio.file.Path
import kotlin.io.path.absolute
import kotlin.io.path.absolutePathString
import kotlin.io.path.readText

context(InterpolateCtx)
fun Yaml.parseAndPreprocess(
    originPath: Path,
    templatePathLoader: (String) -> Path,
): Settings {
    val absoluteOriginPath = originPath.absolute()
    val absoluteTemplateLoader: (String) -> Path = { templatePathLoader(it).absolute() }

    val rootConfig = load<Settings>(absoluteOriginPath.readText())

    val templateNames = rootConfig
        .getValue<List<String>>("apply") ?: emptyList()
    // TODO Remove this when we will move to new version of deft.
    val oldSectionTemplateNames = rootConfig
        .getValue<List<String>>("include") ?: emptyList()

    val allTemplates = templateNames + oldSectionTemplateNames

    val appliedTemplates = allTemplates
        .map(absoluteTemplateLoader)
        .map { it.parent to load<Settings>(it.readText()) }

    val resultConfig = appliedTemplates
        .fold(rootConfig) { acc, from -> mergeTemplate(from.second, acc, from.first) }

    return resultConfig.doInterpolate()
}

/**
 * Simple merge algorithm that do not handle lists at all and just overrides
 * key/value pairs.
 */
private fun mergeTemplate(
    template: Settings,
    origin: Settings,
    templateDir: Path,
    previousPath: String = "",
    // By default, restrict sub templates.
    ignoreTemplateKeys: Collection<String> = setOf("apply", "include")
): Settings = buildMap {
    val allKeys = template.keys + origin.keys

    // Pass all "origin" keys, that are ignored on "template" side.
    ignoreTemplateKeys.forEach { key ->
        origin[key]?.let { put(key, it) }
    }

    // Merge all other keys.
    allKeys.filter { it !in ignoreTemplateKeys }.forEach { key ->
        val nextPath = "$previousPath.$key"
        val templateValue = template[key]
        val originValue = origin[key]
        if (templateValue != null && originValue == null)
            put(key, adjustTemplateValue(templateValue, templateDir))
        else if (originValue != null && templateValue == null)
            put(key, originValue)
        // Need or condition to enable smart casts.
        else if (templateValue == null || originValue == null)
            return@forEach
        else {
            when {
                templateValue is Map<*, *> && originValue is Map<*, *> ->
                    put(
                        key,
                        mergeTemplate(
                            templateValue as Settings,
                            originValue as Settings,
                            templateDir,
                            nextPath
                        )
                    )

                templateValue is List<*> && originValue is List<*> ->
                    put(
                        key,
                        (adjustTemplateValue(templateValue, templateDir) as List<*>) + originValue
                    )

                templateValue::class == originValue::class ->
                    put(key, originValue)

                else -> {
                    error(
                        "Error while merging two configs: " +
                                "Values under path $nextPath have different types." +
                                "First config type: ${templateValue::class.simpleName}. " +
                                "Second config type: ${originValue::class.simpleName}." +
                                "(Maybe type is a container type and there is an inner type conflict)"
                    )
                }
            }
        }
    }
}

/**
 * Make literal adjustments, when applying template, like changing paths.
 */
private fun adjustTemplateValue(
    value: Any,
    templateDir: Path,
): Any = when (value) {
    is List<*> ->
        (value as List<Any>).map { adjustTemplateValue(it, templateDir) }
    is Map<*, *> ->
        (value as Settings).entries
            .associate { it.key to (adjustTemplateValue(it.value, templateDir)) }
    else ->
        adjustTemplateLiteral(value, templateDir)
}

/**
 * Make literal adjustments, when applying template, like changing paths.
 */
private fun adjustTemplateLiteral(
    value: Any,
    templateDir: Path,
) = when {
    value is String && value.startsWith(".") ->
        templateDir.resolve(value).normalize().absolutePathString()
    else ->
        value
}

context(InterpolateCtx)
internal fun Settings.doInterpolate(): Settings =
    transformLeafs { leaf ->
        if (leaf !is String)
            leaf
        else
            leaf.tryInterpolate()
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
