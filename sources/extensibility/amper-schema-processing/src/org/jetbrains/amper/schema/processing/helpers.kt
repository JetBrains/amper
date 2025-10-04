/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.schema.processing

import org.jetbrains.amper.plugins.schema.model.PluginData
import org.jetbrains.kotlin.analysis.api.symbols.KaClassSymbol

interface DeclarationsProvider {
    fun declarationFor(type: PluginData.Type.ObjectType): PluginData.ClassData
    fun declarationFor(type: PluginData.Type.VariantType): PluginData.VariantData
}

internal interface SymbolsCollector {
    fun onEnumReferenced(symbol: KaClassSymbol, name: PluginData.SchemaName)
    fun onClassReferenced(symbol: KaClassSymbol, name: PluginData.SchemaName)
}

class ParsingOptions(
    /**
     * True if we are currently parsing our own Extensibility API declarations as part of the Amper tool build.
     */
    val isParsingAmperApi: Boolean,
)

context(resolver: DeclarationsProvider)
internal val PluginData.Type.ObjectType.declaration get() = resolver.declarationFor(this)

context(resolver: DeclarationsProvider)
internal val PluginData.Type.VariantType.declaration get() = resolver.declarationFor(this)
