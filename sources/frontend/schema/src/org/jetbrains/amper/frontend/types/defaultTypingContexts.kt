/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.types

import org.jetbrains.amper.frontend.contexts.MinimalModule
import org.jetbrains.amper.frontend.customTaskSchema.CustomTaskNode
import org.jetbrains.amper.frontend.plugins.MinimalPluginModule
import org.jetbrains.amper.frontend.plugins.PluginYamlRoot
import org.jetbrains.amper.frontend.plugins.TaskAction
import org.jetbrains.amper.frontend.plugins.generated.ShadowMaps
import org.jetbrains.amper.frontend.schema.Module
import org.jetbrains.amper.frontend.schema.Project
import org.jetbrains.amper.frontend.schema.Template
import org.jetbrains.amper.plugins.schema.model.PluginData
import kotlin.reflect.KClass

/**
 * Typing context that is default for all amper files (project.yaml, module.yaml, templates, custom tasks).
 */
fun SchemaTypingContext(
    pluginData: List<PluginData> = emptyList(),
): SchemaTypingContext = object : ExtensibleBuiltInTypingContext(
    null,
    listOf(
        Module::class,
        Template::class,
        Project::class,
        MinimalModule::class,
        MinimalPluginModule::class,
        CustomTaskNode::class,
    ) + ShadowMaps.PublicInterfaceToShadowNodeClass.values,
) {
    override fun discoverTypes() {
        discoverPluginTypes(pluginData)
        super.discoverTypes()
    }
}.apply { discoverTypes() }

/**
 * Typing context that is specific for plugin.yaml files.
 * It should be separated because other plugin tasks are not "visible" within this context.
 */
@Suppress("FunctionName")
fun PluginYamlTypingContext(
    parent: SchemaTypingContext,
    pluginData: PluginData,
): SchemaTypingContext = object : ExtensibleBuiltInTypingContext(parent, listOf(PluginYamlRoot::class)) {
    override fun discoverTypes() {
        // Task action is replaced with a specific type from the parent, build explicitly for a plugin.yaml file.
        registeredDeclarations[taskActionBuiltInKey] = getDeclaration(pluginData.taskActionPluginKey)
        super.discoverTypes()
    }

    // Currently, only one class can be extended as a custom [SchemaType.VariantType]
    override fun KClass<*>.isVariant() = isSealed || this == TaskAction::class
}.apply { discoverTypes() }