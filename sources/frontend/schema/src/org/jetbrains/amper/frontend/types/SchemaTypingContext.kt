/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.types

import org.jetbrains.amper.frontend.api.Default
import org.jetbrains.amper.frontend.api.SchemaNode
import org.jetbrains.amper.frontend.plugins.ExtensionSchemaNode
import org.jetbrains.amper.frontend.plugins.TaskAction
import org.jetbrains.amper.frontend.plugins.generated.ShadowMaps
import org.jetbrains.amper.frontend.schema.PluginSettings
import org.jetbrains.amper.frontend.types.SchemaObjectDeclaration.Property
import org.jetbrains.amper.frontend.types.generated.DeclarationOfModule
import org.jetbrains.amper.frontend.types.generated.DeclarationOfPluginYamlRoot
import org.jetbrains.amper.frontend.types.generated.DeclarationOfTask
import org.jetbrains.amper.frontend.types.generated.DeclarationOfTemplate
import org.jetbrains.amper.frontend.types.generated.PublicInterfaceToDeclaration
import org.jetbrains.amper.frontend.types.maven.discoverMavenPluginXmlTypes
import org.jetbrains.amper.maven.MavenPluginXml
import org.jetbrains.amper.plugins.schema.model.Defaults
import org.jetbrains.amper.plugins.schema.model.PluginData
import org.jetbrains.amper.plugins.schema.model.SourceLocation

class SchemaTypingContext(
    pluginData: List<PluginData> = emptyList(),
    mavenPlugins: List<MavenPluginXml> = emptyList(),
) {
    private val pluginDeclarations = pluginData.associateBy(
        keySelector = { it.id },
        valueTransform = { PluginDeclarations(it) },
    )

    private val pluginYamlDeclarations = pluginDeclarations.mapValues { (_, declarations) ->
        DeclarationOfPluginYamlRoot(
            pluginSettingsPlaceholderDeclaration = declarations.settingsClassDeclaration,
            taskDeclaration = DeclarationOfTask(
                taskActionDeclaration = SyntheticVariantDeclaration(
                    qualifiedName = "org.jetbrains.amper.frontend.plugins.TaskAction",
                    variants = declarations.pluginData.declarations.tasks.map { taskInfo ->
                        declarations.ObjectDeclaration(
                            data = taskInfo.syntheticType,
                            instantiationStrategy = {
                                TaskAction(taskInfo)
                            },
                        )
                    }
                )
            ),
        )
    }

    private val allPluginSettingsDeclaration = object : BuiltinSchemaObjectDeclarationBase<PluginSettings>() {
        override val qualifiedName get() = "org.jetbrains.amper.frontend.schema.PluginSettings"
        override fun createInstance() = PluginSettings()
        override val properties: List<Property> = pluginDeclarations.map { (id, declarations) ->
            Property(
                name = id.value,
                type = declarations.settingsClassDeclaration.toType(isMarkedNullable = true),
                documentation = declarations.pluginData.description,
                origin = declarations.settingsClassDeclaration.origin,
                default = Default.Static(null),
            )
        } + discoverMavenPluginXmlTypes(mavenPlugins)
    }

    val moduleDeclaration = DeclarationOfModule(
        pluginSettingsDeclaration = allPluginSettingsDeclaration,
    )

    val templateDeclaration = DeclarationOfTemplate(
        pluginSettingsDeclaration = allPluginSettingsDeclaration,
    )

    fun pluginYamlDeclaration(id: PluginData.Id): DeclarationOfPluginYamlRoot {
        return pluginYamlDeclarations[id] ?: error("Plugin with ID $id not found")
    }
}

private class SyntheticVariantDeclaration(
    override val qualifiedName: String,
    override val variants: List<SchemaObjectDeclaration>,
) : SchemaVariantDeclaration {
    override val origin get() = SchemaOrigin.Builtin
    override val variantTree: List<SchemaVariantDeclaration.Variant> =
        variants.map { SchemaVariantDeclaration.Variant.LeafVariant(it) }

    override fun toString() = qualifiedName
}

private class PluginDeclarations(
    val pluginData: PluginData,
) {
    private val classes = mutableMapOf<PluginData.SchemaName, SchemaObjectDeclaration>()
    private val enums = mutableMapOf<PluginData.SchemaName, SchemaEnumDeclaration>()

    private fun toSchemaType(
        type: PluginData.Type,
    ): SchemaType = when (type) {
        is PluginData.Type.BooleanType -> SchemaType.BooleanType(isMarkedNullable = type.isNullable)
        is PluginData.Type.IntType -> SchemaType.IntType(isMarkedNullable = type.isNullable)
        is PluginData.Type.StringType -> SchemaType.StringType(isMarkedNullable = type.isNullable)
        is PluginData.Type.PathType -> SchemaType.PathType(isMarkedNullable = type.isNullable, isTraceableWrapped = true)
        is PluginData.Type.ListType -> SchemaType.ListType(
            elementType = toSchemaType(type.elementType),
            isMarkedNullable = type.isNullable,
        )
        is PluginData.Type.MapType -> SchemaType.MapType(
            valueType = toSchemaType(type.valueType),
            isMarkedNullable = type.isNullable,
        )
        is PluginData.Type.EnumType -> {
            SchemaType.EnumType(
                declaration = ShadowMaps.PublicInterfaceToDeclaration[type.schemaName.qualifiedName]
                        as SchemaEnumDeclaration? ?: enumDeclarationFor(type.schemaName),
                isMarkedNullable = type.isNullable,
            )
        }
        is PluginData.Type.ObjectType -> {
            SchemaType.ObjectType(
                declaration = ShadowMaps.PublicInterfaceToDeclaration[type.schemaName.qualifiedName]
                        as SchemaObjectDeclaration? ?: classDeclarationFor(type.schemaName),
                isMarkedNullable = type.isNullable,
            )
        }
        is PluginData.Type.VariantType -> {
            SchemaType.VariantType(
                // NOTE: plugins do not yet allow custom variant types.
                declaration = ShadowMaps.PublicInterfaceToDeclaration[type.schemaName.qualifiedName]
                        as SchemaVariantDeclaration,
                isMarkedNullable = type.isNullable,
            )
        }
    }

    val settingsClassDeclaration: SchemaObjectDeclaration = pluginData.pluginSettingsSchemaName?.let {
        classDeclarationFor(it)
    } ?: object : SchemaObjectDeclarationBase() {
        override val properties = listOf(
            // TODO: Provide a proper user-friendly origin here,
            //  that would somehow point to the plugin module
            enabledProperty(SchemaOrigin.Builtin)
        )
        override fun createInstance() = ExtensionSchemaNode()
        override val qualifiedName: String get() = "${pluginData.id.value}.Settings"
    }

    fun classDeclarationFor(name: PluginData.SchemaName): SchemaObjectDeclaration = classes.getOrPut(name) {
        ObjectDeclaration(pluginData.declarations.classes.first { it.name == name })
    }

    fun enumDeclarationFor(name: PluginData.SchemaName): SchemaEnumDeclaration = enums.getOrPut(name) {
        EnumDeclaration(pluginData.declarations.enums.first { it.schemaName == name })
    }

    inner class ObjectDeclaration(
        override val schemaName: PluginData.SchemaName,
        properties: List<PluginData.ClassData.Property>,
        override val origin: SchemaOrigin,
        private val instantiationStrategy: () -> SchemaNode,
    ) : SchemaObjectDeclarationBase(), PluginBasedTypeDeclaration {

        constructor(
            data: PluginData.ClassData,
            instantiationStrategy: () -> SchemaNode = ::ExtensionSchemaNode,
        ) : this(
            data.name, data.properties, data.origin.toLocalPluginOrigin(),
            instantiationStrategy
        )

        override val properties: List<Property> by lazy {
            buildList {
                for (property in properties) {
                    if (property.internalAttributes?.isProvided == true) {
                        // Skip @Provided properties

                        // WARNING: This code is currently unreachable because we use "shadow" schema nodes to model
                        // builtin API schema.
                        // There @Provided properties are represented as @IgnoreForSchema ones, so they are ignored earlier.
                        continue
                    }
                    this += Property(
                        name = property.name,
                        type = toSchemaType(property.type),
                        default = property.default.toInternalDefault(forType = property.type),
                        documentation = property.doc,
                        hasShorthand = property.internalAttributes?.isShorthand == true,
                        isFromKeyAndTheRestNested = property.internalAttributes?.isDependencyNotation == true,
                        inputOutputMark = property.inputOutputMark,
                        origin = property.origin.toLocalPluginOrigin(),
                        // All user properties can be referenced
                        canBeReferenced = true,
                    )
                }
                if (schemaName == pluginData.pluginSettingsSchemaName) {
                    // Add a synthetic `enabled` property if this is a plugin schema extension
                    this += enabledProperty(origin)
                }
            }
        }

        override fun createInstance(): SchemaNode = instantiationStrategy()
        override fun toString() = qualifiedName
    }

    private fun enabledProperty(origin: SchemaOrigin) = Property(
        name = "enabled",
        type = SchemaType.BooleanType(),
        default = Default.Static(false),
        documentation = "Whether to enable the `${pluginData.id.value}` plugin",
        hasShorthand = true,
        origin = origin,
    )

    private class EnumDeclaration(
        private val data: PluginData.EnumData,
    ) : SchemaEnumDeclarationBase(), PluginBasedTypeDeclaration {
        override val schemaName: PluginData.SchemaName
            get() = data.schemaName
        override val origin: SchemaOrigin = data.origin.toLocalPluginOrigin()
        override val entries: List<SchemaEnumDeclaration.EnumEntry> by lazy {
            data.entries.map { entry ->
                SchemaEnumDeclaration.EnumEntry(
                    name = entry.name,
                    schemaValue = entry.schemaName,
                    documentation = entry.doc,
                    origin = entry.origin.toLocalPluginOrigin(),
                )
            }
        }
        override val isOrderSensitive get() = false
        override fun toEnumConstant(name: String): Nothing? = null
        override fun toString() = qualifiedName
    }
}

private fun Defaults?.toInternalDefault(forType: PluginData.Type): Default? {
    if (this != null) {
        return Default.Static(toValue())
    }

    if (forType.isNullable) {
        // Nullable types are `null` by default
        return Default.Static(null)
    }

    if (forType is PluginData.Type.ObjectType) {
        // For non-nullable objects we instantiate a nested object by default, like with `by nested()`
        // Note: the real type will be taken out of the property type, so it's not important what to put here.
        return Default.NestedObject
    }

    return null
}

private fun Defaults.toValue(): Any? = when(this) {
    is Defaults.BooleanDefault -> value
    is Defaults.EnumDefault -> value
    is Defaults.StringDefault -> value
    is Defaults.IntDefault -> value
    is Defaults.ListDefault -> value.map { it.toValue() }
    is Defaults.MapDefault -> value.mapValues { (_, it) -> it.toValue() }
    Defaults.Null -> null
}

private fun SourceLocation?.toLocalPluginOrigin(): SchemaOrigin {
    if (this == null) return SchemaOrigin.Builtin
    return SchemaOrigin.LocalPlugin(
        sourceFile = path,
        textRange = textRange,
    )
}
