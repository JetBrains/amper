package org.jetbrains.deft.proto.frontend

import org.jetbrains.deft.proto.core.DeftException
import org.jetbrains.deft.proto.core.Result
import org.jetbrains.deft.proto.core.getOrElse
import org.jetbrains.deft.proto.core.messages.ProblemReporterContext
import org.jetbrains.deft.proto.frontend.nodes.*
import org.yaml.snakeyaml.Yaml
import java.nio.file.Path
import kotlin.io.path.absolute
import kotlin.io.path.exists
import kotlin.io.path.reader

context(ProblemReporterContext)
private fun Yaml.loadFile(path: Path): Result<YamlNode.Mapping> {
    if (!path.exists()) {
        problemReporter.reportError(FrontendYamlBundle.message("cant.find.template", path))
        return Result.failure(DeftException())
    }

    val node = compose(path.reader())?.toYamlNode() ?: YamlNode.Mapping.Empty
    if (node !is YamlNode.Mapping) {
        problemReporter.reportError(FrontendYamlBundle.message("invalid.yaml", path), file = path)
        return Result.failure(DeftException())
    }
    return Result.success(node)
}

context(InterpolateCtx, BuildFileAware, ProblemReporterContext)
fun Yaml.parseAndPreprocess(
    originPath: Path,
    templatePathLoader: (String) -> Path,
): Result<Settings> {
    val absoluteOriginPath = originPath.absolute()
    val rootConfig = loadFile(absoluteOriginPath).getOrElse { return Result.failure(DeftException()) }

    val templateNames = rootConfig["apply"]
    if (templateNames !is YamlNode.Sequence?) {
        problemReporter.reportError(
            FrontendYamlBundle.message("apply.must.be.sequence", templateNames?.nodeType ?: "null"),
            file = originPath,
            line = templateNames?.startMark?.line ?: 0
        )
        return Result.failure(DeftException())
    }

    var hasBrokenTemplates = false
    val appliedTemplates = templateNames
        ?.mapNotNull { templatePath ->
            if (templatePath !is YamlNode.Scalar) {
                problemReporter.reportError(
                    FrontendYamlBundle.message("apply.template.should.be.path", templatePath.nodeType),
                    file = originPath,
                    line = templatePath.startMark.line + 1
                )
                return@mapNotNull null
            }

            val path = templatePathLoader(templatePath.value).absolute()
            val template = loadFile(path.normalize())
            template.getOrElse {
                hasBrokenTemplates = true
                problemReporter.reportError(
                    FrontendYamlBundle.message("cant.apply.template", templatePath.value),
                    file = originPath,
                    line = templatePath.startMark.line + 1
                )
                null
            }?.let { path.parent to it }
        } ?: emptyList()
    if (hasBrokenTemplates) return Result.failure(DeftException())

    val resultConfig: Settings = appliedTemplates
        .fold(rootConfig.toSettings()) { acc: Settings, from -> mergeTemplate(from.second.toSettings(), acc, from.first) }

    return Result.success(resultConfig.doInterpolate())
}

/**
 * Simple merge algorithm that do not handle lists at all and just overrides
 * key/value pairs.
 */
context (BuildFileAware)
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
context (BuildFileAware)
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
context (BuildFileAware)
private fun adjustTemplateLiteral(
    value: Any,
    templateDir: Path,
) = when {
    value is String && value.startsWith(".") -> {
        buildFile.parent.relativize(templateDir.resolve(value).normalize()).toString()
    }
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
