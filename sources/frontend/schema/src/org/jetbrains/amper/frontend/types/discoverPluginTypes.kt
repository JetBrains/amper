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
        // Load external classes.
        for (declaration in pluginData.classTypes) registeredDeclarations[pluginData.id / declaration.name] =
            ExternalObjectDeclaration(
                pluginId = pluginData.id,
                data = declaration,
                instantiationStrategy = { ExtensionSchemaNode(declaration.name.qualifiedName) },
                isRootSchema = declaration.name == pluginData.moduleExtensionSchemaName,
                typingContext = this,
            )

        // Add a stub class just for the `enabled` sake.
        if (pluginData.moduleExtensionSchemaName == null) registeredDeclarations[pluginData.id / StubSchemaName] =
            ExternalObjectDeclaration(
                pluginId = pluginData.id,
                data = PluginData.ClassData(name = StubSchemaName),
                instantiationStrategy = { ExtensionSchemaNode(null) },
                isRootSchema = true,
                typingContext = this,
            )

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
        val pluginSettingsExtensionSchemaName = pluginData.moduleExtensionSchemaName ?: StubSchemaName
        val pluginSettingsExtensionKey = pluginData.id / pluginSettingsExtensionSchemaName
        addCustomProperty(
            settingsTypeKey,
            ExtensibleBuiltInTypingContext.CustomPropertyDescriptor(
                pluginData.id.value,
                pluginSettingsExtensionKey,
                pluginData.description,
            )
        )
    }
}

private class ExternalObjectDeclaration(
    private val pluginId: PluginData.Id,
    private val data: PluginData.ClassData,
    private val instantiationStrategy: () -> SchemaNode,
    private val isRootSchema: Boolean,
    private val typingContext: ExtensibleBuiltInTypingContext,
) : SchemaObjectDeclaration {
    override val properties: List<SchemaObjectDeclaration.Property> by lazy {
        buildList {
            for (property in data.properties) {
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

    private val propertiesByName by lazy { properties.associateBy { it.name } }

    override fun getProperty(name: String): SchemaObjectDeclaration.Property? = propertiesByName[name]

    override fun createInstance(): SchemaNode = instantiationStrategy()
    override val qualifiedName get() = data.name.qualifiedName
    override fun toString() = qualifiedName
}

private class ExternalEnumDeclaration(
    private val data: PluginData.EnumData,
) : SchemaEnumDeclaration {
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
        keyType = SchemaType.StringType(),
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