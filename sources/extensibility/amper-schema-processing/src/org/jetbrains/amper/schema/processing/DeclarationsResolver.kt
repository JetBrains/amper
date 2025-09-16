/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.schema.processing

import org.jetbrains.amper.plugins.schema.model.PluginData

internal class DeclarationsResolver(
    declarations: PluginData.Declarations,
) {
    private val classes = declarations.classes.associateBy { it.name }
    private val variants = declarations.variants.associateBy { it.name }

    fun resolve(type: PluginData.Type.ObjectType): PluginData.ClassData {
        return classes[type.schemaName] ?: error("Class not found: ${type.schemaName}")
    }

    fun resolve(type: PluginData.Type.VariantType): PluginData.VariantData {
        return variants[type.schemaName] ?: error("Enum not found: ${type.schemaName}")
    }
}

context(resolver: DeclarationsResolver)
internal val PluginData.Type.ObjectType.declaration get() = resolver.resolve(this)

context(resolver: DeclarationsResolver)
internal val PluginData.Type.VariantType.declaration get() = resolver.resolve(this)
