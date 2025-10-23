/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.types

import org.jetbrains.amper.frontend.contexts.MinimalModule
import org.jetbrains.amper.frontend.plugins.MavenPluginXml
import org.jetbrains.amper.frontend.plugins.MinimalPluginModule
import org.jetbrains.amper.frontend.plugins.PluginYamlRoot
import org.jetbrains.amper.frontend.plugins.Task
import org.jetbrains.amper.frontend.plugins.TaskAction
import org.jetbrains.amper.frontend.plugins.generated.ShadowClasspath
import org.jetbrains.amper.frontend.plugins.generated.ShadowCompilationArtifact
import org.jetbrains.amper.frontend.plugins.generated.ShadowDependencyLocal
import org.jetbrains.amper.frontend.plugins.generated.ShadowMaps
import org.jetbrains.amper.frontend.plugins.generated.ShadowModuleSources
import org.jetbrains.amper.frontend.schema.Module
import org.jetbrains.amper.frontend.schema.Project
import org.jetbrains.amper.frontend.schema.Template
import org.jetbrains.amper.frontend.types.maven.discoverMavenPluginXmlTypes
import org.jetbrains.amper.plugins.schema.model.PluginData
import kotlin.reflect.KClass

/**
 * Typing context that is default for all amper files (project.yaml, module.yaml, templates, custom tasks).
 */
fun SchemaTypingContext(
    pluginData: List<PluginData> = emptyList(),
    mavenPlugins: List<MavenPluginXml> = emptyList(),
): SchemaTypingContext = object : ExtensibleBuiltInTypingContext(
    null,
    listOf(
        Module::class,
        Template::class,
        Project::class,
        MinimalModule::class,
        MinimalPluginModule::class,
    ) + ShadowMaps.PublicInterfaceToShadowNodeClass.values,
) {
    override fun discoverTypes() {
        discoverPluginTypes(pluginData)
        discoverMavenPluginXmlTypes(mavenPlugins)
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
        addCustomProperty(
            Task::class.builtInKey,
            CustomPropertyDescriptor(
                propertyName = Task.TASK_OUTPUT_DIR,
                propertyType = SchemaType.PathType,
                documentation = "Dedicated task directory under the build root",
                origin = SchemaOrigin.Builtin,
                canBeReferenced = true,
                isUserSettable = false,
            )
        )

        val pluginSettingsDeclarationKey = pluginData.pluginSettingsSchemaName?.let { pluginSettingsName ->
            pluginData.id / pluginSettingsName
        }
        val moduleReferenceDeclaration = ModuleDataForPluginDeclaration(
            classpathType = { getDeclaration<ShadowClasspath>().toType() },
            localDependencyType = { getDeclaration<ShadowDependencyLocal>().toType() },
            compilationArtifactType = { getDeclaration<ShadowCompilationArtifact>().toType() },
            moduleSourcesType = { getDeclaration<ShadowModuleSources>().toType() },
        ).also { registeredDeclarations[ModuleDataForPluginDeclaration] = it }

        addCustomProperty(
            PluginYamlRoot::class.builtInKey,
            CustomPropertyDescriptor(
                propertyName = PluginYamlRoot.MODULE,
                propertyType = moduleReferenceDeclaration.toType(),
                documentation = "Data from the module the plugin is applied to",
                origin = SchemaOrigin.Builtin,
                isUserSettable = false,
            ),
        )

        if (pluginSettingsDeclarationKey != null) {
            addCustomProperty(
                PluginYamlRoot::class.builtInKey,
                CustomPropertyDescriptor(
                    propertyName = PluginYamlRoot.PLUGIN_SETTINGS,
                    propertyType = getDeclaration(pluginSettingsDeclarationKey).toType(),
                    documentation = "Plugin settings as configured in the module",
                    origin = SchemaOrigin.Builtin,
                    isUserSettable = false,
                    canBeReferenced = true,
                ),
            )
        }

        // Task action is replaced with a specific type from the parent, build explicitly for a plugin.yaml file.
        registeredDeclarations[taskActionBuiltInKey] = getDeclaration(pluginData.taskActionPluginKey)
        super.discoverTypes()
    }

    // Currently, only one class can be extended as a custom [SchemaType.VariantType]
    override fun KClass<*>.isVariant() = isSealed || this == TaskAction::class
}.apply { discoverTypes() }