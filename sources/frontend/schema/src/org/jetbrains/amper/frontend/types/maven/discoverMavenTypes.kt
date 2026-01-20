/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.types.maven

import org.jetbrains.amper.frontend.api.Default
import org.jetbrains.amper.frontend.plugins.AmperMavenPluginDescription
import org.jetbrains.amper.frontend.plugins.AmperMavenPluginMojo
import org.jetbrains.amper.frontend.plugins.ExtensionSchemaNode
import org.jetbrains.amper.frontend.types.DeclarationKey
import org.jetbrains.amper.frontend.types.ExtensibleBuiltInTypingContext
import org.jetbrains.amper.frontend.types.SchemaObjectDeclaration
import org.jetbrains.amper.frontend.types.SchemaObjectDeclarationBase
import org.jetbrains.amper.frontend.types.SchemaOrigin
import org.jetbrains.amper.frontend.types.SchemaType
import org.jetbrains.amper.frontend.types.pluginSettingsTypeKey
import org.jetbrains.amper.frontend.types.withNullability
import org.jetbrains.amper.maven.MavenPluginXml
import org.jetbrains.amper.maven.Mojo
import java.io.File

data class MavenDeclarationKey(val artifactId: String, val mojoImplementation: String) : DeclarationKey

private typealias StringType = SchemaType.StringType

internal fun ExtensibleBuiltInTypingContext.discoverMavenPluginXmlTypes(pluginXmls: List<MavenPluginXml>) = apply {
    pluginXmls.forEach { discoverMavenPluginXmlTypes(it) }
}

internal fun ExtensibleBuiltInTypingContext.discoverMavenPluginXmlTypes(plugin: MavenPluginXml) = apply {
    plugin.mojos.forEach { mojo ->
        val properties = mojo.parameters.filter { it.editable }.mapNotNull { parameter ->
            val isNullable = !parameter.required
            val (type, defaultValue) = when (parameter.type) {
                "boolean" -> SchemaType.BooleanType(isNullable) to false
                "int" -> SchemaType.IntType(isNullable) to 0
                "java.lang.String" -> StringType(isNullable) to ""
                "java.lang.String[]" -> SchemaType.ListType(StringType(), isNullable) to emptyArray<String>()
                "java.io.File" -> SchemaType.PathType(isNullable) to File(".")
                "java.io.File[]" -> SchemaType.ListType(SchemaType.PathType(), isNullable) to emptyArray<File>()
                "java.nio.Path" -> SchemaType.PathType(isNullable) to File(".")
                "java.nio.Path[]" -> SchemaType.ListType(SchemaType.PathType(), isNullable) to emptyArray<File>()
                "java.util.Map" -> SchemaType.MapType(
                    StringType(),
                    StringType(),
                    isNullable
                ) to emptyMap<String, String>()
                "java.util.List" -> SchemaType.ListType(StringType(), isNullable) to emptyList<String>()
                else -> return@mapNotNull null
            }
            val propertyConfig = mojo.configuration.parameterValues.singleOrNull { it.parameterName == parameter.name }
            val finalDefault = if (isNullable) Default.Static(null)
            else if (propertyConfig?.defaultValue != null) Default.Static(defaultValue)
            else null
            
            SchemaObjectDeclaration.Property(
                name = parameter.name,
                type = type,
                documentation = parameter.description,
                default = finalDefault,
                origin = SchemaOrigin.MavenPlugin,
            )
        }

        val mavenDeclarationKey = MavenDeclarationKey(plugin.artifactId, mojo.implementation)
        val enabledProperty = SchemaObjectDeclaration.Property(
            name = "enabled",
            type = SchemaType.BooleanType(),
            default = Default.Static(false),
            hasShorthand = true,
            origin = SchemaOrigin.MavenPlugin,
        )
        val mavenDeclaration = MavenSchemaObjectDeclaration(
            mojo.implementation,
            properties + enabledProperty,
        )
        registeredDeclarations[mavenDeclarationKey] = mavenDeclaration

        addCustomProperty(
            pluginSettingsTypeKey,
            ExtensibleBuiltInTypingContext.CustomPropertyDescriptor(
                // TODO Think about uniqueness of mojos goal identifiers.
                propertyName = amperMavenPluginId(plugin, mojo),
                propertyType = mavenDeclaration.toType().withNullability(isMarkedNullable = true),
                documentation = mojo.description,
                origin = SchemaOrigin.MavenPlugin,
                default = Default.Static(null),
            )
        )
    }
}

/**
 * ID string of a Maven mojo that is applied as an Amper plugin.
 */
fun amperMavenPluginId(plugin: AmperMavenPluginDescription, mojo: AmperMavenPluginMojo): String =
    "${plugin.artifactId}.${mojo.goal}"

fun amperMavenPluginId(plugin: MavenPluginXml, mojo: Mojo): String =
    "${plugin.artifactId}.${mojo.goal}"

private class MavenSchemaObjectDeclaration(
    private val mojoImplementation: String,
    override val properties: List<SchemaObjectDeclaration.Property>,
    override val origin: SchemaOrigin = SchemaOrigin.MavenPlugin,
) : SchemaObjectDeclarationBase() {
    override val qualifiedName by ::mojoImplementation
    override fun createInstance() = ExtensionSchemaNode()
}