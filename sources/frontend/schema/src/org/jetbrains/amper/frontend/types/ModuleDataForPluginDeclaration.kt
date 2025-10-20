/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.types

import org.jetbrains.amper.frontend.plugins.ExtensionSchemaNode

/**
 * A special synthetic declaration to expose module's data for plugins.
 */
internal class ModuleDataForPluginDeclaration(
    pluginSettingsType: SchemaType?,
    classpathType: () -> SchemaType,
    localDependencyType: () -> SchemaType,
) : SchemaObjectDeclarationBase() {
    override val properties by lazy {
        buildList properties@ {
            fun addProperty(name: String, type: SchemaType, documentation: String) {
                this@properties += SchemaObjectDeclaration.Property(
                    name = name,
                    type = type,
                    origin = SchemaOrigin.Builtin,
                    documentation = documentation,
                    default = null,
                    canBeReferenced = true,
                    // NOTE: `isUserSettable = false` is not necessary here, because all these properties are guarded
                    //  from modification by the fact that `module` itself is `isUserSettable = false`.
                )
            }

            // TODO: Write more detailed documentation for these properties.
            if (pluginSettingsType != null) {
                addProperty(PLUGIN_SETTINGS, pluginSettingsType, "Plugin settings as configured in the module")
            }
            addProperty(NAME, SchemaType.StringType, "Name of the module")
            addProperty(ROOT_DIR, SchemaType.PathType, "Module's root directory (where `module.yaml` resides)")
            addProperty(RUNTIME_CLASSPATH, classpathType(), "Runtime classpath of the module")
            addProperty(SELF, localDependencyType(), "Dependency on the module itself")
        }
    }

    override fun createInstance() = ExtensionSchemaNode().also { it.schemaType = this }
    override val qualifiedName get() = "ModuleDataForPlugin"
    override val origin get() = SchemaOrigin.Builtin

    companion object : DeclarationKey {
        const val PLUGIN_SETTINGS = "configuration"
        const val NAME = "name"
        const val ROOT_DIR = "rootDir"
        const val RUNTIME_CLASSPATH = "classpath"
        const val SELF = "dependency"
    }
}