/*
 * Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.types.maven

import org.jetbrains.amper.frontend.api.Default
import org.jetbrains.amper.frontend.plugins.AmperMavenPluginDescription
import org.jetbrains.amper.frontend.plugins.AmperMavenPluginMojo
import org.jetbrains.amper.frontend.plugins.ExtensionSchemaNode
import org.jetbrains.amper.frontend.types.SchemaObjectDeclaration
import org.jetbrains.amper.frontend.types.SchemaObjectDeclarationBase
import org.jetbrains.amper.frontend.types.SchemaOrigin
import org.jetbrains.amper.frontend.types.SchemaType
import org.jetbrains.amper.frontend.types.withNullability
import org.jetbrains.amper.maven.MavenPluginXml
import org.jetbrains.amper.maven.Mojo
import org.jetbrains.amper.maven.Parameter
import java.nio.file.Path
import kotlin.io.path.Path

private typealias StringType = SchemaType.StringType

internal fun discoverMavenPluginXmlTypes(pluginXmls: List<MavenPluginXml>) =
    pluginXmls.flatMap { discoverMavenPluginXmlTypes(it) }

internal fun discoverMavenPluginXmlTypes(plugin: MavenPluginXml) =
    plugin.mojos.map { mojo ->
        val properties = mojo.parameters.filter { it.editable }.mapNotNull { parameter ->
            val isNullable = !parameter.required
            val (type, defaultValue) = parameter.calculateTypeWithDefault() ?: return@mapNotNull null

            val propertyConfig = mojo.configuration.parameterValues.singleOrNull { it.parameterName == parameter.name }
            val finalDefault = if (isNullable) Default.Static(null)
            // Here default value is set only to calm Amper validation, since actual defaults
            // will be calculated by Maven code.
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

        SchemaObjectDeclaration.Property(
            name = amperMavenPluginId(plugin, mojo),
            type = mavenDeclaration.toType().withNullability(isMarkedNullable = true),
            documentation = mojo.description,
            origin = SchemaOrigin.MavenPlugin,
            default = Default.Static(null),
        )
    }

internal const val PlexusConfigurationFqn = "org.codehaus.plexus.configuration.PlexusConfiguration"

internal fun Parameter.calculateTypeWithDefault(): Pair<SchemaType, Any>? = when (type) {
    "boolean" -> return SchemaType.BooleanType(!required) to false
    "int" -> return SchemaType.IntType(!required) to 0
    "java.lang.String" -> return StringType(!required) to ""
    "java.lang.String[]" -> return SchemaType.ListType(StringType(), !required) to emptyList<String>()
    "java.io.File" -> return SchemaType.PathType(!required) to Path(".")
    "java.io.File[]" -> return SchemaType.ListType(SchemaType.PathType(), !required) to emptyList<Path>()
    "java.nio.Path" -> return SchemaType.PathType(!required) to Path(".")
    "java.nio.Path[]" -> return SchemaType.ListType(SchemaType.PathType(), !required) to emptyList<Path>()
    "java.util.Map" -> return SchemaType.MapType(
        StringType(),
        StringType(),
        !required
    ) to emptyMap<String, String>()
    "java.util.List" -> return SchemaType.ListType(StringType(), !required) to emptyList<String>()
    PlexusConfigurationFqn -> StringType(
        isMarkedNullable = !required,
        semantics = SchemaType.StringType.Semantics.MavenPlexusConfigXml,
    ) to ""
    else -> return null
}

/**
 * ID string of a Maven mojo that is applied as an Amper plugin.
 */
fun amperMavenPluginId(plugin: AmperMavenPluginDescription, mojo: AmperMavenPluginMojo): String =
    "${plugin.artifactId}.${mojo.goal}"

fun amperMavenPluginId(plugin: MavenPluginXml, mojo: Mojo): String =
    "${plugin.artifactId}.${mojo.goal}"

internal class MavenSchemaObjectDeclaration(
    private val mojoImplementation: String,
    override val properties: List<SchemaObjectDeclaration.Property>,
    override val origin: SchemaOrigin = SchemaOrigin.MavenPlugin,
) : SchemaObjectDeclarationBase() {
    override val qualifiedName by ::mojoImplementation
    override fun createInstance() = ExtensionSchemaNode()
}