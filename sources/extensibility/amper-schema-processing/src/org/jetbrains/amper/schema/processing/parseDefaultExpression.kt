/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.schema.processing

import org.jetbrains.amper.plugins.schema.model.Defaults
import org.jetbrains.amper.plugins.schema.model.PluginData
import org.jetbrains.amper.plugins.schema.model.PluginDataResponse.DiagnosticKind.WarningRedundant
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.base.KaConstantValue
import org.jetbrains.kotlin.analysis.api.resolution.KaSimpleFunctionCall
import org.jetbrains.kotlin.analysis.api.resolution.KaSimpleVariableAccessCall
import org.jetbrains.kotlin.analysis.api.resolution.KaSuccessCallInfo
import org.jetbrains.kotlin.analysis.api.resolution.symbol
import org.jetbrains.kotlin.analysis.api.symbols.KaEnumEntrySymbol
import org.jetbrains.kotlin.psi.KtExpression

context(session: KaSession, _: DiagnosticsReporter)
internal fun parseDefaultExpression(
    expression: KtExpression,
    type: PluginData.Type,
    nestedExpression: Boolean = false,
): Defaults? {
    val constant by lazy { with(session) { expression.evaluate() } }

    val call by lazy { with(session) { expression.resolveToCall() as? KaSuccessCallInfo }?.call }

    if (type.isNullable && constant is KaConstantValue.NullValue) {
        if (!nestedExpression) {
            report(expression, "schema.defaults.redundant.null", kind = WarningRedundant)
        }
        return Defaults.Null
    }
    return when (type) {
        is PluginData.Type.BooleanType -> (constant as? KaConstantValue.BooleanValue)
            ?.let { Defaults.BooleanDefault(it.value) }
            ?: run { reportError(expression, "schema.defaults.invalid.constant"); null }
        is PluginData.Type.IntType -> (constant as? KaConstantValue.IntValue)
            ?.let { Defaults.IntDefault(it.value) }
            ?: run { reportError(expression, "schema.defaults.invalid.constant"); null }
        is PluginData.Type.StringType -> (constant as? KaConstantValue.StringValue)
            ?.let { Defaults.StringDefault(it.value) }
            ?: run { reportError(expression, "schema.defaults.invalid.constant"); null }
        is PluginData.Type.EnumType -> when (val symbol = (call as? KaSimpleVariableAccessCall)?.symbol) {
            is KaEnumEntrySymbol -> Defaults.EnumDefault(symbol.name.identifier)
            else -> {
                reportError(expression, "schema.defaults.invalid.enum"); null
            }
        }
        is PluginData.Type.ListType -> (call as? KaSimpleFunctionCall)?.let { call ->
            when (call.symbol.callableId) {
                EMPTY_LIST -> Defaults.ListDefault(emptyList())
                LIST_OF -> Defaults.ListDefault(call.argumentMapping.mapNotNull { (e, _) ->
                    parseDefaultExpression(e, type.elementType, nestedExpression = true)
                })
                else -> null
            }
        } ?: run{ reportError(expression, "schema.defaults.invalid.list"); null }
        is PluginData.Type.MapType -> (call as? KaSimpleFunctionCall)?.let { call ->
            when (call.symbol.callableId) {
                EMPTY_MAP -> Defaults.MapDefault(emptyMap())
                // TODO: MAP_OF
                else -> null
            }
        } ?: run { reportError(expression, "schema.defaults.invalid.map"); null }
        is PluginData.Type.ObjectType -> {
            reportError(expression, "schema.defaults.invalid.object"); null
        }
        is PluginData.Type.PathType -> {
            reportError(expression, "schema.defaults.invalid.path"); null
        }
    }
}
