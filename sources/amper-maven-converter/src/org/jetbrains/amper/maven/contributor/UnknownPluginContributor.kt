/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.maven.contributor

import org.apache.maven.model.InputLocation
import org.apache.maven.model.Plugin
import org.apache.maven.model.PluginExecution
import org.apache.maven.project.MavenProject
import org.codehaus.plexus.util.xml.Xpp3Dom
import org.jetbrains.amper.frontend.schema.Module
import org.jetbrains.amper.frontend.tree.SyntheticBuilder
import org.jetbrains.amper.frontend.types.SchemaType
import org.jetbrains.amper.maven.MavenConverterBundle
import org.jetbrains.amper.maven.MavenPluginXml
import org.jetbrains.amper.maven.Mojo
import org.jetbrains.amper.maven.ProjectTreeBuilder
import org.jetbrains.amper.maven.YamlComment
import org.slf4j.LoggerFactory
import javax.xml.stream.XMLInputFactory
import javax.xml.stream.XMLStreamConstants.CDATA
import javax.xml.stream.XMLStreamConstants.CHARACTERS
import javax.xml.stream.XMLStreamConstants.END_ELEMENT
import javax.xml.stream.XMLStreamConstants.START_ELEMENT
import javax.xml.stream.XMLStreamReader
import kotlin.io.path.Path
import kotlin.io.path.div
import kotlin.io.path.exists
import kotlin.io.path.inputStream

private val logger = LoggerFactory.getLogger("UnknownPluginContributor")

val unsupportedVariables = setOf($$"${basedir}", $$"${project.build.outputDirectory}")

internal fun ProjectTreeBuilder.contributeUnknownPlugins(
    reactorProjects: Set<MavenProject>,
    pluginXmls: List<MavenPluginXml>,
) {
    val pluginXmlMap = pluginXmls.associateBy { "${it.groupId}:${it.artifactId}" }

    reactorProjects.filterJarProjects().forEach { project ->
        module(project.basedir.toPath() / "module.yaml") {
            project.buildPlugins.forEach { plugin ->
                val pluginXml = pluginXmlMap["${plugin.groupId}:${plugin.artifactId}"]
                if (pluginXml != null) {
                    contributeUnknownPlugin(plugin, pluginXml)
                }
            }
        }
    }
}

private fun ProjectTreeBuilder.ModuleTreeBuilder.contributeUnknownPlugin(
    plugin: Plugin,
    pluginXml: MavenPluginXml,
) {
    val executions = plugin.executions
    val pluginConfiguration = plugin.configuration as? Xpp3Dom

    val goalToExecution = mutableMapOf<String, MutableList<PluginExecution>>()

    executions.forEach { execution ->
        execution.goals.forEach { goal ->
            goalToExecution.getOrPut(goal) { mutableListOf() }.add(execution)
        }
    }

    val duplicateGoals = goalToExecution.filter { it.value.size > 1 }.keys

    duplicateGoals.forEach { goal ->
        val convertedExecution = goalToExecution[goal]?.firstOrNull()
        val skippedExecutions = goalToExecution[goal]?.drop(1) ?: emptyList()

        logger.warn(
            "Plugin ${plugin.groupId}:${plugin.artifactId} has goal [$goal] configured in multiple executions. " +
                    "Only goals configured in a single execution are supported for automatic conversion."
        )

        addYamlComment(
            createDuplicateExecutionComment(
                plugin,
                goal,
                convertedExecution?.id,
                skippedExecutions,
            )
        )
    }

    pluginXml.mojos.forEach { mojo ->
        val goal = mojo.goal
        val execution = goalToExecution[goal]?.firstOrNull()

        val configuration: Xpp3Dom? = when {
            execution != null -> (execution.configuration as? Xpp3Dom) ?: pluginConfiguration
            mojo.phase != null -> pluginConfiguration
            else -> null
        }

        contributePluginMojo(pluginXml, mojo, configuration)
    }
}

private fun ProjectTreeBuilder.ModuleTreeBuilder.contributePluginMojo(
    pluginXml: MavenPluginXml,
    mojo: Mojo,
    configuration: Xpp3Dom?,
) {
    val pluginKey = "${pluginXml.artifactId}.${mojo.goal}"

    val hasConfiguration = configuration != null && configuration.children.any { child ->
        // maven ignores unknown configuration keys
        if (child is Xpp3Dom) {
            val parameter = mojo.parameters.find { it.name == child.name }
            parameter != null && parameter.editable
        } else {
            false
        }
    }

    withDefaultContext {
        `object`<Module> {
            Module::plugins {
                if (hasConfiguration) {
                    pluginKey setTo map(SchemaType.MapType(SchemaType.StringType, SchemaType.StringType)) {
                        "enabled" setTo scalar(true)
                        mapConfiguration(this, configuration, mojo)
                    }
                } else {
                    pluginKey setTo scalar("enabled") // shorthand
                }
            }
        }
    }
}

private fun SyntheticBuilder.mapConfiguration(
    builder: SyntheticBuilder.MapLikeValueBuilder,
    configuration: Xpp3Dom,
    mojo: Mojo,
) {
    configuration.children.forEach { child ->
        if (child is Xpp3Dom) {
            val parameter = mojo.parameters.find { it.name == child.name }
            if (parameter != null && parameter.editable) {
                mapConfigurationValue(builder, child, parameter.type)
            }
        }
    }
}

private fun SyntheticBuilder.mapConfigurationValue(
    builder: SyntheticBuilder.MapLikeValueBuilder,
    element: Xpp3Dom,
    type: String,
) {
    builder.apply {
        when (type) {
            "boolean" -> {
                element.value?.let { value ->
                    element.name setTo scalar(value.toBoolean())
                }
            }
            "int", "java.lang.Integer" -> {
                element.value?.let { value ->
                    element.name setTo scalar(value)
                }
            }
            "java.lang.String", "java.io.File", "java.nio.Path" -> {
                element.value()?.let { value ->
                    element.name setTo scalar(value)
                }
            }
            "java.lang.String[]", "java.util.List" -> {
                element.name setTo list(SchemaType.ListType(SchemaType.StringType)) {
                    element.children.forEach { child ->
                        if (child is Xpp3Dom) {
                            child.value()?.let { add(scalar(it)) }
                        }
                    }
                }
            }
            "java.io.File[]", "java.nio.Path[]" -> {
                element.name setTo list(SchemaType.ListType(SchemaType.PathType())) {
                    element.children.forEach { child ->
                        if (child is Xpp3Dom) {
                            child.value()?.let { add(scalar(it)) }
                        }
                    }
                }
            }
            "java.util.Map" -> {
                element.name setTo map(SchemaType.MapType(SchemaType.StringType, SchemaType.StringType)) {
                    element.children.forEach { child ->
                        if (child is Xpp3Dom && child.value != null) {
                            child.name setTo scalar(child.value)
                        }
                    }
                }
            }
            else -> {
                mapPlexusConfiguration(this@mapConfigurationValue, builder, element)
            }
        }
    }
}


private fun Xpp3Dom.value(): String? {
    val originalValue = (inputLocation as? InputLocation)?.originalValue()
    return if (originalValue != null && unsupportedVariables.any { it in originalValue }) {
        originalValue
    } else {
        value
    }
}

private fun mapPlexusConfiguration(
    syntheticBuilder: SyntheticBuilder,
    builder: SyntheticBuilder.MapLikeValueBuilder,
    element: Xpp3Dom,
) {
    with(syntheticBuilder) {
        builder.apply {
            if (element.value != null) {
                element.name setTo scalar(element.value)
            } else if (element.childCount > 0) {
                val childNames = element.children.filterIsInstance<Xpp3Dom>().map { it.name }.distinct()

                if (childNames.size == 1 && element.childCount > 1) {
                    element.name setTo list(SchemaType.ListType(SchemaType.StringType)) {
                        element.children.forEach { child ->
                            if (child is Xpp3Dom) {
                                if (child.value != null) {
                                    add(scalar(child.value))
                                }
                            }
                        }
                    }
                } else {
                    element.name setTo map(SchemaType.MapType(SchemaType.StringType, SchemaType.StringType)) {
                        element.children.forEach { child ->
                            if (child is Xpp3Dom) {
                                mapPlexusConfiguration(syntheticBuilder, this, child)
                            }
                        }
                    }
                }
            }

        }
    }
}


internal fun InputLocation.originalValue(): String? {
    val sourcePath = source?.location ?: return null
    val path = Path(sourcePath)
    if (!path.exists()) return null

    val targetLine = lineNumber
    val targetColumn = columnNumber

    try {
        val factory = XMLInputFactory.newInstance()
        path.inputStream().use { inputStream ->
            val reader = factory.createXMLStreamReader(inputStream)
            while (reader.hasNext()) {
                val event = reader.next()
                if (event == START_ELEMENT) {
                    val location = reader.location
                    if (location.lineNumber == targetLine) {
                        val elementNameLength = reader.localName.length
                        // <elementName>
                        val expectedMavenColumn = location.columnNumber + elementNameLength + 2
                        if (expectedMavenColumn == targetColumn) {
                            return readElementText(reader)
                        }
                    }
                }
            }
        }
    } catch (e: Exception) {
        logger.warn("Failed to read original value from $sourcePath at line $targetLine, column $targetColumn", e)
    }

    return null
}

private fun readElementText(reader: XMLStreamReader): String? {
    val content = StringBuilder()
    var depth = 0

    while (reader.hasNext() && depth >= 0) {
        when (reader.next()) {
            CHARACTERS,
            CDATA -> if (depth == 0) { content.append(reader.text) }
            START_ELEMENT -> depth++
            END_ELEMENT -> depth--
        }
    }

    val result = content.toString().trim()
    return result.ifEmpty { null }
}

private fun createDuplicateExecutionComment(
    plugin: Plugin,
    goal: String,
    convertedExecutionId: String?,
    skippedExecutions: List<PluginExecution>,
): YamlComment {
    val path = listOf("plugins", "${plugin.artifactId}.$goal")

    val beforeComment = MavenConverterBundle.message(
        "duplicate.execution.warning",
        plugin.groupId,
        plugin.artifactId,
        goal,
        convertedExecutionId?.let { "[$it]" } ?: ""
    )

    val afterComment = if (skippedExecutions.isNotEmpty()) {
        buildString {
            appendLine(MavenConverterBundle.message("duplicate.execution.not.translated"))
            skippedExecutions.forEach { execution ->
                execution.getLocation("")?.let { location ->
                    appendLine(MavenConverterBundle.message("duplicate.execution.reference", location.source.location, location.lineNumber, location.columnNumber))
                }
                appendLine("<execution>")
                appendLine("  <id>${execution.id}</id>")
                execution.phase?.let { appendLine("  <phase>$it</phase>") }
                execution.inherited?.let { inherited ->
                    appendLine("  <inherited>${inherited != "false"}</inherited>")
                }
                appendLine("  <goals>")
                appendLine("    <goal>$goal</goal>")
                appendLine("  </goals>")
                (execution.configuration as? Xpp3Dom)?.let { config ->
                    val configXml = config.toString()
                        .removePrefix("""<?xml version="1.0" encoding="UTF-8"?>""")
                        .trim()
                        .prependIndent("  ")
                    appendLine(configXml)
                }
                appendLine("</execution>")
            }
        }.trim()
    } else null

    return YamlComment(path, beforeComment, afterComment)
}
