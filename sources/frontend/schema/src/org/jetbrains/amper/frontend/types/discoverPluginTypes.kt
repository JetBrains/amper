/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.types

import org.jetbrains.amper.frontend.api.Default
import org.jetbrains.amper.frontend.api.SchemaNode
import org.jetbrains.amper.frontend.plugins.ExtensionSchemaNode
import org.jetbrains.amper.frontend.plugins.TaskAction
import org.jetbrains.amper.frontend.plugins.generated.ShadowMaps
import org.jetbrains.amper.plugins.schema.model.Defaults
import org.jetbrains.amper.plugins.schema.model.PluginData
import org.jetbrains.amper.plugins.schema.model.SourceLocation
import kotlin.reflect.KClass

internal data class PluginKey(val pluginId: PluginData.Id, val qualifiedName: String) : DeclarationKey

internal val PluginData.taskActionPluginKey get() = id / TaskAction::class
internal val taskActionBuiltInKey = TaskAction::class.builtInKey

internal operator fun PluginData.Id.div(schemaName: PluginData.SchemaName) = PluginKey(this, schemaName.qualifiedName)
internal operator fun PluginData.Id.div(kClass: KClass<*>) = PluginKey(this, kClass.qualifiedName!!)

/**
 * Iterate over all [PluginData] and register all discovered types into [this] typing context.
 */
internal fun ExtensibleBuiltInTypingContext.discoverPluginTypes(pluginsData: List<PluginData>) {
    pluginsData.forEach { pluginData ->
        val moduleExtensionSchemaName = pluginData.moduleExtensionSchemaName
        // Load external classes.
        for (declaration in pluginData.declarations.classes) registeredDeclarations[pluginData.id / declaration.name] =
            ExternalObjectDeclaration(
                pluginId = pluginData.id,
                data = declaration,
                instantiationStrategy = { ExtensionSchemaNode() },
                isRootSchema = declaration.name == moduleExtensionSchemaName,
                typingContext = this,
            )

        // TODO: Provide a proper user-friendly origin here,
        //  that would somehow point to the plugin module
        val stubPluginConfigurationOrigin = SchemaOrigin.Builtin

        val pluginSettingsExtensionSchemaName = if (moduleExtensionSchemaName == null) {
            // Add a stub class just for the `enabled` sake.
            val stubSchemaName = PluginData.SchemaName(
                packageName = pluginData.id.value,
                simpleNames = listOf("Settings"),
            )
            registeredDeclarations[pluginData.id / stubSchemaName] = ExternalObjectDeclaration(
                pluginId = pluginData.id,
                schemaName = stubSchemaName,
                properties = emptyList(),
                origin = stubPluginConfigurationOrigin,
                instantiationStrategy = { ExtensionSchemaNode() },
                isRootSchema = true,
                typingContext = this,
            )
            stubSchemaName
        } else moduleExtensionSchemaName

        // Load external enums.
        for (declaration in pluginData.declarations.enums) {
            registeredDeclarations[pluginData.id / declaration.schemaName] = ExternalEnumDeclaration(declaration)
        }

        // Load external variant classes.
        registeredDeclarations[pluginData.taskActionPluginKey] =
            SyntheticVariantDeclaration(
                qualifiedName = pluginData.taskActionPluginKey.qualifiedName,
                variants = pluginData.declarations.tasks.map { taskInfo ->
                    ExternalObjectDeclaration(
                        pluginId = pluginData.id,
                        data = taskInfo.syntheticType,
                        instantiationStrategy = {
                            TaskAction(taskInfo)
                        },
                        isRootSchema = false,
                        typingContext = this,
                    )
                }
            )

        val moduleExtensionSchemaDeclaration = moduleExtensionSchemaName?.let { name ->
            pluginData.declarations.classes.find { it.name == name }
        }
        // Load custom properties for a [PluginSettings] schema type.
        val pluginSettingsDeclarationKey = pluginData.id / pluginSettingsExtensionSchemaName
        val pluginSettingsType = checkNotNull(registeredDeclarations[pluginSettingsDeclarationKey]) {
            "No declaration is present for $pluginSettingsDeclarationKey"
        }.toType()
        addCustomProperty(
            pluginSettingsTypeKey,
            ExtensibleBuiltInTypingContext.CustomPropertyDescriptor(
                propertyName = pluginData.id.value,
                propertyType = pluginSettingsType.withNullability(isMarkedNullable = true),
                documentation = pluginData.description,
                origin = moduleExtensionSchemaDeclaration?.origin?.toLocalPluginOrigin()
                    ?: stubPluginConfigurationOrigin,
                default = Default.Static(null),
            )
        )
    }
}

private class ExternalObjectDeclaration(
    private val pluginId: PluginData.Id,
    override val schemaName: PluginData.SchemaName,
    properties: List<PluginData.ClassData.Property>,
    override val origin: SchemaOrigin,
    private val instantiationStrategy: () -> SchemaNode,
    private val isRootSchema: Boolean,
    private val typingContext: ExtensibleBuiltInTypingContext,
) : SchemaObjectDeclarationBase(), PluginBasedTypeDeclaration {

    constructor(
        pluginId: PluginData.Id,
        data: PluginData.ClassData,
        instantiationStrategy: () -> SchemaNode,
        isRootSchema: Boolean,
        typingContext: ExtensibleBuiltInTypingContext,
    ) : this(
        pluginId, data.name, data.properties, data.origin.toLocalPluginOrigin(),
        instantiationStrategy, isRootSchema, typingContext
    )

    override val properties: List<SchemaObjectDeclaration.Property> by lazy {
        buildList {
            for (property in properties) {
                if (property.internalAttributes?.isProvided == true) {
                    // Skip @Provided properties

                    // WARNING: This code is currently unreachable because we use "shadow" schema nodes to model
                    // builtin API schema.
                    // There @Provided properties are represented as @IgnoreForSchema ones, so they are ignored earlier.
                    continue
                }
                this += SchemaObjectDeclaration.Property(
                    name = property.name,
                    type = typingContext.toSchemaType(pluginId, property.type),
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
            if (isRootSchema) {
                // Add a synthetic `enabled` property if this is a plugin schema extension
                this += SchemaObjectDeclaration.Property(
                    name = "enabled",
                    type = SchemaType.BooleanType(),
                    default = Default.Static(false),
                    documentation = "Whether to enable the `${pluginId.value}` plugin",
                    hasShorthand = true,
                    origin = origin,
                )
            }
        }
    }

    private fun Defaults?.toInternalDefault(forType: PluginData.Type): Default<*>? {
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
            return Default.NestedObject(ExtensionSchemaNode::class)
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

    override fun createInstance(): SchemaNode = instantiationStrategy().apply {
        schemaType = this@ExternalObjectDeclaration
    }
    override fun toString() = qualifiedName
}

private class ExternalEnumDeclaration(
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
    override fun toEnumConstant(name: String) = name
    override fun toString() = qualifiedName
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
        valueType = toSchemaType(pluginId, type.valueType),
        isMarkedNullable = type.isNullable,
    )
    is PluginData.Type.EnumType -> {
        val key = type.schemaName.toKeyShadowAware(pluginId)
        SchemaType.EnumType(
            declaration = checkNotNull(registeredDeclarations[key] as? SchemaEnumDeclaration),
            isMarkedNullable = type.isNullable,
        )
    }
    is PluginData.Type.ObjectType -> {
        val key = type.schemaName.toKeyShadowAware(pluginId)
        SchemaType.ObjectType(
            declaration = checkNotNull(registeredDeclarations[key] as? SchemaObjectDeclaration),
            isMarkedNullable = type.isNullable,
        )
    }
    is PluginData.Type.VariantType -> TODO("Not yet allowed for the user types, so not reached here")
}

private fun PluginData.SchemaName.toKeyShadowAware(pluginId: PluginData.Id) =
    // Check if the qualified name is in the ShadowMap. If so, the we "shadow" it with the builtin "shadow" schema node.
    ShadowMaps.PublicInterfaceToShadowNodeClass[qualifiedName]?.builtInKey
        ?: (pluginId / this)

private fun SourceLocation?.toLocalPluginOrigin(): SchemaOrigin {
    if (this == null) return SchemaOrigin.Builtin
    return SchemaOrigin.LocalPlugin(
        sourceFile = path,
        textRange = textRange,
    )
}