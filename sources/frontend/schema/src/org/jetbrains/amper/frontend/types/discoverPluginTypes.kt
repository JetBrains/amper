/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.types

import org.jetbrains.amper.frontend.api.Default
import org.jetbrains.amper.frontend.api.SchemaNode
import org.jetbrains.amper.frontend.plugins.ExtensionSchemaNode
import org.jetbrains.amper.frontend.plugins.TaskAction
import org.jetbrains.amper.plugins.schema.model.Defaults
import org.jetbrains.amper.plugins.schema.model.PluginData

internal data class PluginKey(val pluginId: PluginData.Id, val qualifiedName: String) : DeclarationKey

internal val PluginData.taskActionPluginKey get() = id / PluginData.SchemaName(TaskAction::class.qualifiedName!!)
internal val taskActionBuiltInKey = TaskAction::class.builtInKey

private val StubSchemaName = PluginData.SchemaName("Stub")

internal operator fun PluginData.Id.div(schemaName: PluginData.SchemaName) = PluginKey(this, schemaName.qualifiedName)

/**
 * Iterate over all [PluginData] and register all discovered types into [this] typing context.
 */
internal fun ExtensibleBuiltInTypingContext.discoverPluginTypes(pluginsData: List<PluginData>) {
    pluginsData.forEach { pluginData ->
        val moduleExtensionSchemaName = pluginData.moduleExtensionSchemaName
        val hasValidModuleExtension = moduleExtensionSchemaName != null &&
                pluginData.classTypes.any { it.name == moduleExtensionSchemaName }
        // Load external classes.
        for (declaration in pluginData.classTypes) registeredDeclarations[pluginData.id / declaration.name] =
            ExternalObjectDeclaration(
                pluginId = pluginData.id,
                data = declaration,
                instantiationStrategy = { ExtensionSchemaNode(declaration.name.qualifiedName) },
                isRootSchema = declaration.name == moduleExtensionSchemaName,
                typingContext = this,
            )

        // Add a stub class just for the `enabled` sake.
        val pluginSettingsExtensionSchemaName = if (!hasValidModuleExtension) {
            registeredDeclarations[pluginData.id / StubSchemaName] = ExternalObjectDeclaration(
                pluginId = pluginData.id,
                schemaName = StubSchemaName,
                properties = emptyList(),
                instantiationStrategy = { ExtensionSchemaNode(null) },
                isRootSchema = true,
                typingContext = this,
            )
            StubSchemaName
        } else moduleExtensionSchemaName

        // Load external enums.
        for (declaration in pluginData.enumTypes) registeredDeclarations[pluginData.id / declaration.schemaName] =
            ExternalEnumDeclaration(declaration)

        // Load external variant classes.
        registeredDeclarations[pluginData.taskActionPluginKey] =
            SyntheticVariantDeclaration(
                qualifiedName = pluginData.taskActionPluginKey.qualifiedName,
                variants = pluginData.tasks.map { taskInfo ->
                    ExternalObjectDeclaration(
                        pluginId = pluginData.id,
                        data = taskInfo.syntheticType,
                        instantiationStrategy = {
                            TaskAction(
                                jvmFunctionName = taskInfo.jvmFunctionName,
                                jvmOwnerClassName = taskInfo.jvmFunctionClassName,
                                inputPropertyNames = taskInfo.inputPropertyNames,
                                outputPropertyNames = taskInfo.outputPropertyNames,
                            )
                        },
                        isRootSchema = false,
                        typingContext = this,
                    )
                }
            )

        // Load custom properties for a [Settings] schema type.
        addCustomProperty(
            settingsTypeKey,
            ExtensibleBuiltInTypingContext.CustomPropertyDescriptor(
                propertyName = pluginData.id.value,
                propertyType = pluginData.id / pluginSettingsExtensionSchemaName,
                description = pluginData.description,
            )
        )
    }
}

private class ExternalObjectDeclaration(
    private val pluginId: PluginData.Id,
    schemaName: PluginData.SchemaName,
    properties: List<PluginData.ClassData.Property>,
    private val instantiationStrategy: () -> SchemaNode,
    private val isRootSchema: Boolean,
    private val typingContext: ExtensibleBuiltInTypingContext,
) : SchemaObjectDeclarationBase() {

    constructor(
        pluginId: PluginData.Id,
        data: PluginData.ClassData,
        instantiationStrategy: () -> SchemaNode,
        isRootSchema: Boolean,
        typingContext: ExtensibleBuiltInTypingContext,
    ) : this(pluginId, data.name, data.properties, instantiationStrategy, isRootSchema, typingContext)

    override val properties: List<SchemaObjectDeclaration.Property> by lazy {
        buildList {
            for (property in properties) {
                this += SchemaObjectDeclaration.Property(
                    name = property.name,
                    type = typingContext.toSchemaType(pluginId, property.type),
                    default = property.default?.let { Default.Static(it.toValue()) },
                    documentation = property.doc,
                )
            }
            if (isRootSchema) {
                // Add a synthetic `enabled` property if this is a plugin schema extension
                this += SchemaObjectDeclaration.Property(
                    name = "enabled",
                    type = SchemaType.BooleanType(),
                    default = Default.Static(false),
                    documentation = "Whether to enable the `${pluginId.value}` plugin",
                    hasShorthand = true,
                )
            }
        }
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

    override fun createInstance(): SchemaNode = instantiationStrategy()
    override val qualifiedName = schemaName.qualifiedName
    override fun toString() = qualifiedName
}

private class ExternalEnumDeclaration(
    private val data: PluginData.EnumData,
) : SchemaEnumDeclarationBase() {
    override val entries: List<SchemaEnumDeclaration.EnumEntry> by lazy {
        data.entries.map { entry ->
            SchemaEnumDeclaration.EnumEntry(
                name = entry.name,
                schemaValue = entry.schemaName,
                documentation = entry.doc,
            )
        }
    }
    override val isOrderSensitive get() = false
    override fun toEnumConstant(name: String) = name
    override val qualifiedName get() = data.schemaName.qualifiedName
    override fun toString() = qualifiedName
}

private class SyntheticVariantDeclaration(
    override val qualifiedName: String,
    override val variants: List<SchemaObjectDeclaration>,
) : SchemaVariantDeclaration {
    override val variantTree: List<SchemaVariantDeclaration.Variant> =
        variants.map { SchemaVariantDeclaration.Variant.LeafVariant(it) }

    override fun toString() = qualifiedName
}

private fun ExtensibleBuiltInTypingContext.toSchemaType(
    pluginId: PluginData.Id,
    type: PluginData.Type,
): SchemaType = when (type) {
    is PluginData.Type.BooleanType -> SchemaType.BooleanType(isMarkedNullable = type.isNullable)
    is PluginData.Type.IntType -> SchemaType.IntType(isMarkedNullable = type.isNullable)
    is PluginData.Type.StringType -> SchemaType.StringType(isMarkedNullable = type.isNullable)
    is PluginData.Type.PathType -> SchemaType.PathType(isMarkedNullable = type.isNullable)
    is PluginData.Type.ListType -> SchemaType.ListType(
        elementType = toSchemaType(pluginId, type.elementType),
        isMarkedNullable = type.isNullable,
    )
    is PluginData.Type.MapType -> SchemaType.MapType(
        keyType = SchemaType.KeyStringType,
        valueType = toSchemaType(pluginId, type.valueType),
        isMarkedNullable = type.isNullable,
    )
    is PluginData.Type.EnumType -> SchemaType.EnumType(
        declaration = checkNotNull(registeredDeclarations[pluginId / type.schemaName] as? SchemaEnumDeclaration),
        isMarkedNullable = type.isNullable,
    )
    is PluginData.Type.ObjectType -> SchemaType.ObjectType(
        declaration = checkNotNull(registeredDeclarations[pluginId / type.schemaName] as? SchemaObjectDeclaration),
        isMarkedNullable = type.isNullable,
    )
}