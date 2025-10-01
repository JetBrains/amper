/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.types.maven

import org.jetbrains.amper.frontend.api.Default
import org.jetbrains.amper.frontend.plugins.ExtensionSchemaNode
import org.jetbrains.amper.frontend.plugins.MavenPluginXml
import org.jetbrains.amper.frontend.plugins.Mojo
import org.jetbrains.amper.frontend.types.DeclarationKey
import org.jetbrains.amper.frontend.types.ExtensibleBuiltInTypingContext
import org.jetbrains.amper.frontend.types.SchemaObjectDeclaration
import org.jetbrains.amper.frontend.types.SchemaObjectDeclarationBase
import org.jetbrains.amper.frontend.types.SchemaOrigin
import org.jetbrains.amper.frontend.types.SchemaType
import org.jetbrains.amper.frontend.types.settingsTypeKey

data class MavenDeclarationKey(val artifactId: String, val mojoImplementation: String) : DeclarationKey

private typealias StringType = SchemaType.StringType

internal fun ExtensibleBuiltInTypingContext.discoverMavenPluginXmlTypes(pluginXmls: List<MavenPluginXml>) = apply {
    pluginXmls.forEach { discoverMavenPluginXmlTypes(it) }
}

internal fun ExtensibleBuiltInTypingContext.discoverMavenPluginXmlTypes(plugin: MavenPluginXml) = apply {
    plugin.mojos.forEach { mojo ->
        val properties = mojo.parameters.filter { it.editable }.mapNotNull { it ->
            val isNullable = !it.required
            val type = when (it.type) {
                "boolean" -> SchemaType.BooleanType(isNullable)
                "int" -> SchemaType.IntType(isNullable)
                "java.lang.String" -> StringType(isNullable)
                "java.lang.String[]" -> SchemaType.ListType(StringType(), isNullable)
                "java.io.File" -> SchemaType.PathType(isNullable)
                "java.io.File[]" -> SchemaType.ListType(SchemaType.PathType(), isNullable)
                "java.util.Map" -> SchemaType.MapType(StringType(), StringType(), isNullable)
                "java.util.List" -> SchemaType.ListType(StringType(), isNullable)
                else -> return@mapNotNull null
            }
            SchemaObjectDeclaration.Property(
                name = it.name,
                type = type,
                documentation = it.description,
                default = if (isNullable) Default.Static(null) else null,
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
        registeredDeclarations[mavenDeclarationKey] =
            MavenSchemaObjectDeclaration(
                mojo.implementation,
                properties + enabledProperty,
            )

        addCustomProperty(
            settingsTypeKey,
            ExtensibleBuiltInTypingContext.CustomPropertyDescriptor(
                // TODO Think about uniqueness of mojos goal identifiers.
                propertyName = mavenCompatPluginId(plugin, mojo),
                propertyType = mavenDeclarationKey,
                description = mojo.description,
                origin = SchemaOrigin.MavenPlugin,
            )
        )
    }
}

/**
 * Id string of a compatibility maven plugin that is launching a given mojo.
 */
fun mavenCompatPluginId(plugin: MavenPluginXml, mojo: Mojo): String = "${plugin.artifactId}.${mojo.goal}"

private class MavenSchemaObjectDeclaration(
    private val mojoImplementation: String,
    override val properties: List<SchemaObjectDeclaration.Property>,
    override val origin: SchemaOrigin = SchemaOrigin.MavenPlugin,
) : SchemaObjectDeclarationBase() {
    override val qualifiedName by ::mojoImplementation
    override fun createInstance() = ExtensionSchemaNode()
}