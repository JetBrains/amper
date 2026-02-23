/*
 * Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.types

import org.jetbrains.amper.plugins.schema.model.PluginData

interface PluginBasedTypeDeclaration : SchemaTypeDeclaration {
    val schemaName: PluginData.SchemaName

    override val publicInterfaceReflectionName: String
        get() = schemaName.reflectionName()

    override val displayName: String
        get() = schemaName.simpleNames.joinToString(".")

    override val qualifiedName: String
        get() = schemaName.qualifiedName
}