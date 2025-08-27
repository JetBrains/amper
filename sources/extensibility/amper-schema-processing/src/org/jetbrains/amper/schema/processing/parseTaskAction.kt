/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.schema.processing

import org.jetbrains.amper.plugins.schema.model.PluginData
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.kdoc.parser.KDocKnownTag
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtNamedFunction

context(session: KaSession, _: DiagnosticsReporter, symbolsCollector: SymbolsCollector)
internal fun parseTaskAction(function: KtNamedFunction): PluginData.TaskInfo? {
    if (!function.isTopLevel) return null.also { reportError(function, "schema.task.action.not.toplevel") }
    val name = function.fqName?.asString() ?: return null  // invalid Kotlin (top-level functions are named)
    function.extensionReceiver()?.let { reportError(it, "schema.forbidden.task.action.extension") }
    function.modifierList?.let { modifiers ->
        modifiers.getModifier(KtTokens.SUSPEND_KEYWORD)?.let {
            reportError(it, "schema.forbidden.task.action.suspend")
        }
        modifiers.getModifier(KtTokens.INLINE_KEYWORD)?.let {
            reportError(it, "schema.forbidden.task.action.inline")
        }
    }
    function.typeParameterList?.let { reportError(it, "schema.forbidden.task.action.generic") }
    function.contextReceiverList?.let { reportError(it, "schema.forbidden.task.action.context.receivers") }

    val returnType = with(session) { function.returnType }
    if (with(session) { !returnType.isUnitType }) {
        reportError(function.typeReference ?: function, "schema.forbidden.task.action.return")
    }

    val inputNames = mutableListOf<String>()
    val outputNames = mutableListOf<String>()
    val properties = buildList {
        for (parameter in function.valueParameters) {
            val parameterName = parameter.name ?: continue // invalid Kotlin
            val typeReference = parameter.typeReference ?: continue // invalid Kotlin
            val type = with(session) { typeReference.type }.parseSchemaType(origin = { typeReference }) ?: continue
            val isPath = type is PluginData.Type.PathType
            val inputMark = parameter.getAnnotation(INPUT_ANNOTATION_CLASS)
            val outputMark = parameter.getAnnotation(OUTPUT_ANNOTATION_CLASS)
            if (!isPath && (inputMark != null || outputMark != null)) {
                reportError(inputMark ?: outputMark!!, "schema.task.action.parameter.not.path")
            }
            if (isPath && inputMark != null && outputMark != null) {
                reportError(inputMark, "schema.task.action.parameter.path.conflicting")
            }
            if (isPath && inputMark == null && outputMark == null) {
                reportError(parameter, "schema.task.action.parameter.path.unmarked")
            }

            if (isPath && inputMark != null && outputMark == null) inputNames += parameterName
            if (isPath && inputMark == null && outputMark != null) outputNames += parameterName

            val default = parameter.defaultValue?.let { defaultValue ->
                parseDefaultExpression(defaultValue, type)
            }

            add(
                PluginData.ClassData.Property(
                    name = parameterName,
                    type = type,
                    default = default,
                    doc = function.docComment?.findSectionByTag(KDocKnownTag.PARAM, parameterName)?.getContent(),
                )
            )
        }
    }
    return PluginData.TaskInfo(
        syntheticType = PluginData.ClassData(
            name = PluginData.SchemaName(name),
            properties = properties,
            doc = function.getDefaultDocString(),
        ),
        jvmFunctionName = function.name!!,  // FIXME: How to take JvmName into account here?
        jvmFunctionClassName = @OptIn(KaExperimentalApi::class) with(session) {
            function.symbol.containingJvmClassName
        }!!,  // TODO: Fill this only for backend
        inputPropertyNames = inputNames,
        outputPropertyNames = outputNames,
    )
}