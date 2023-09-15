package org.jetbrains.deft.proto.frontend

import org.jetbrains.deft.proto.core.DeftException
import org.jetbrains.deft.proto.core.Result
import org.jetbrains.deft.proto.core.getOrElse
import org.jetbrains.deft.proto.core.messages.ProblemReporterContext
import org.jetbrains.deft.proto.core.templateName
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
    return if (node.castOrReport<YamlNode.Mapping>(path) { FrontendYamlBundle.message("element.name.pot") }) {
        Result.success(node)
    } else {
        Result.failure(DeftException())
    }
}

context(BuildFileAware, ProblemReporterContext)
fun Yaml.parseAndPreprocess(
    originPath: Path,
    templatePathLoader: (String) -> Path,
): Result<Settings> {
    val absoluteOriginPath = originPath.absolute()
    val rootConfig = loadFile(absoluteOriginPath).getOrElse { return Result.failure(DeftException()) }

    val templateNames = rootConfig["apply"]
    if (!templateNames.castOrReport<YamlNode.Sequence?>(originPath) { FrontendYamlBundle.message("element.name.apply") }) {
        return Result.failure(DeftException())
    }

    var hasBrokenTemplates = false
    val appliedTemplates = templateNames
        ?.mapNotNull { templatePath ->
            if (!templatePath.castOrReport<YamlNode.Scalar>(originPath) { FrontendYamlBundle.message("element.name.template.path") }) {
                hasBrokenTemplates = true
                return@mapNotNull null
            }

            val path = templatePathLoader(templatePath.value).absolute().normalize()
            val template = loadFile(path)
            template.getOrElse {
                hasBrokenTemplates = true
                problemReporter.reportError(
                    FrontendYamlBundle.message("cant.apply.template", templatePath.value),
                    file = originPath,
                    line = templatePath.startMark.line + 1
                )
                null
            }?.let { path to it }
        } ?: emptyList()
    if (hasBrokenTemplates) return Result.failure(DeftException())

    var currentConfig = rootConfig
    var hasErrors = false
    appliedTemplates.forEach { (templatePath, template) ->
        val newConfig = mergeTemplate(template, currentConfig, templatePath).getOrElse {
            hasErrors = true
            currentConfig
        }
        currentConfig = newConfig
    }
    if (hasErrors) return Result.failure(DeftException())
    return Result.success(currentConfig.toSettings())
}

/**
 * Simple merge algorithm that do not handle lists at all and just overrides
 * key/value pairs.
 */
context (BuildFileAware, ProblemReporterContext)
private fun mergeTemplate(
    template: YamlNode.Mapping,
    origin: YamlNode.Mapping,
    templatePath: Path,
    currentKeyPath: String = "",
    // By default, restrict sub templates.
    ignoreTemplateKeys: Collection<String> = setOf("apply", "include"),
): Result<YamlNode.Mapping> {
    var hasProblems = false

    val mappings = buildList {
        val allKeys = template.keys + origin.keys

        // Pass all "origin" keys that are ignored on the "template" side.
        ignoreTemplateKeys.forEach { templateKey ->
            origin.getMapping(templateKey)?.let(::add)
        }

        // Merge all other keys.
        allKeys.filter { it !in ignoreTemplateKeys }.forEach { key ->
            val nextKeyPath = if (currentKeyPath.isNotBlank()) "$currentKeyPath.$key" else key
            val templateMapping = template.getMapping(key)
            val originMapping = origin.getMapping(key)
            when {
                templateMapping != null && originMapping == null -> add(
                    templateMapping.first to adjustTemplateValue(
                        templateMapping.second,
                        templatePath
                    )
                )

                originMapping != null && templateMapping == null -> add(originMapping)
                templateMapping != null && originMapping != null -> {
                    val (_, templateValue) = templateMapping
                    val (originKey, originValue) = originMapping

                    when {
                        templateValue is YamlNode.Mapping && originValue is YamlNode.Mapping ->
                            mergeTemplate(templateValue, originValue, templatePath, nextKeyPath).getOrElse {
                                hasProblems = true
                                null
                            }?.let { add(originKey to it) }

                        templateValue is YamlNode.Sequence && originValue is YamlNode.Sequence -> {
                            val adjustedList = adjustTemplateValue(templateValue, templatePath) as YamlNode.Sequence
                            add(originKey to originValue.copy(elements = adjustedList.elements + originValue.elements))
                        }

                        templateValue::class == originValue::class -> add(originMapping)

                        else -> {
                            hasProblems = true
                            problemReporter.reportError(
                                FrontendYamlBundle.message(
                                    "cant.merge.templates",
                                    templatePath.templateName,
                                    nextKeyPath,
                                    originValue.nodeType,
                                    templateValue.nodeType,
                                    "$templatePath:${templateValue.startMark.line + 1}",
                                ),
                                file = buildFile,
                                line = originValue.startMark.line + 1
                            )
                        }
                    }
                }
            }
        }
    }

    if (hasProblems) return Result.failure(DeftException())

    return Result.success(
        YamlNode.Mapping(
            mappings,
            template.startMark,
            origin.endMark
        )
    )
}

/**
 * Make literal adjustments, when applying template, like changing paths.
 */
context (BuildFileAware)
private fun adjustTemplateValue(
    node: YamlNode,
    templatePath: Path,
): YamlNode = when (node) {
    is YamlNode.Mapping -> node.copy(mappings = node.mappings.map { (k, v) ->
        k to adjustTemplateValue(v, templatePath)
    })

    is YamlNode.Sequence -> node.copy(elements = node.elements.map { adjustTemplateValue(it, templatePath) })
    is YamlNode.Scalar -> node.copy(value = adjustTemplateLiteral(node.value, templatePath))
}

/**
 * Make literal adjustments, when applying template, like changing paths.
 */
context (BuildFileAware)
private fun adjustTemplateLiteral(
    value: String,
    templatePath: Path,
): String = when {
    value.startsWith(".") -> {
        buildFile.parent.relativize(templatePath.parent.resolve(value).normalize()).toString()
    }

    else -> value
}
