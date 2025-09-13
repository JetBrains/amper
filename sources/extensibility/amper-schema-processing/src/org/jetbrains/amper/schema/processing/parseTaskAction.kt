/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.schema.processing

import org.jetbrains.amper.plugins.schema.model.PluginData
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.annotations.KaAnnotationValue
import org.jetbrains.kotlin.analysis.api.symbols.KaSymbolVisibility
import org.jetbrains.kotlin.kdoc.parser.KDocKnownTag
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.psiUtil.visibilityModifier

context(session: KaSession, _: DiagnosticsReporter, _: SymbolsCollector, _: ParsedClassesResolver)
internal fun parseTaskAction(function: KtNamedFunction): PluginData.TaskInfo? {
    if (!function.isTopLevel) return null.also { reportError(function, "schema.task.action.not.toplevel") }
    val nameIdentifier = function.nameIdentifier ?: return null // invalid Kotlin (top-level functions are named)
    val name = function.fqName?.asString() ?: return null  // invalid Kotlin (top-level functions are named)
    when (with(session) { function.symbol }.visibility) {
        KaSymbolVisibility.PUBLIC, KaSymbolVisibility.UNKNOWN -> Unit // okay/ignore
        else -> reportError(function.visibilityModifier() ?: nameIdentifier, "schema.task.action.must.be.public")
    }
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

    val inputNames = mutableSetOf<String>()
    val outputNames = mutableSetOf<String>()
    val properties = function.valueParameters.mapNotNull {
        val (property, mark) = parseTaskParameter(
            parameter = it,
            docProvider = { name -> function.docComment?.findSectionByTag(KDocKnownTag.PARAM, name)?.getContent() },
        ) ?: return@mapNotNull null
        when (mark) {
            InputOutputMark.Input -> inputNames.add(property.name)
            InputOutputMark.Output -> outputNames.add(property.name)
            null -> {}
        }
        property
    }

    val optOutOfExecutionAvoidance = with(session) { function.symbol }.annotations
        .first { it.classId == TASK_ACTION_ANNOTATION_CLASS }
        .arguments.find { it.name == TASK_ACTION_EXEC_AVOIDANCE_PARAM }
        .let {
            when (val value = it?.expression) {
                is KaAnnotationValue.EnumEntryValue -> value.callableId == EXEC_AVOIDANCE_DISABLED
                else -> false
            }
        }

    return PluginData.TaskInfo(
        syntheticType = PluginData.ClassData(
            name = PluginData.SchemaName(name),
            properties = properties,
            doc = function.getDefaultDocString(),
            origin = nameIdentifier.getSourceLocation(),
        ),
        jvmFunctionName = function.name!!,  // FIXME: How to take JvmName into account here?
        jvmFunctionClassName = @OptIn(KaExperimentalApi::class) with(session) {
            function.symbol.containingJvmClassName
        }!!,  // TODO: Fill this only for backend
        inputPropertyNames = inputNames,
        outputPropertyNames = outputNames,
        optOutOfExecutionAvoidance = optOutOfExecutionAvoidance,
    )
}

context(session: KaSession, _: DiagnosticsReporter, _: SymbolsCollector, _: ParsedClassesResolver)
private fun parseTaskParameter(
    parameter: KtParameter,
    docProvider: (name: String) -> String?,
): TaskParameter? {
    val parameterName = parameter.name ?: return null // invalid Kotlin
    val typeReference = parameter.typeReference ?: return null // invalid Kotlin
    val type = with(session) { typeReference.type }.parseSchemaType(origin = { typeReference }) ?: return null
    val inputMark = parameter.getAnnotation(INPUT_ANNOTATION_CLASS)
    val outputMark = parameter.getAnnotation(OUTPUT_ANNOTATION_CLASS)
    val inputOutputMark: InputOutputMark? = if (type.containsPath()) {
        when {
            inputMark != null && outputMark != null -> {  // both
                reportError(parameter, "schema.task.action.parameter.path.conflicting")
                null
            }
            inputMark != null -> InputOutputMark.Input  // input only
            outputMark != null -> InputOutputMark.Output  // output only
            else -> {  // none
                reportError(parameter, "schema.task.action.parameter.path.unmarked")
                null
            }
        }
    } else {
        if (outputMark != null) reportError(outputMark, "schema.task.action.parameter.not.path")
        if (inputMark != null) reportError(inputMark, "schema.task.action.parameter.not.path")
        null
    }

    val default = parameter.defaultValue?.let { defaultValue ->
        parseDefaultExpression(defaultValue, type)
    }

    return TaskParameter(
        property = PluginData.ClassData.Property(
            name = parameterName,
            type = type,
            default = default,
            doc = docProvider(parameterName),
            origin = parameter.getSourceLocation(),
        ),
        inputOutputMark = inputOutputMark
    )
}

private data class TaskParameter(
    val property: PluginData.ClassData.Property,
    val inputOutputMark: InputOutputMark?,
)

private enum class InputOutputMark {
    Input,
    Output,
}

context(resolver: ParsedClassesResolver)
private fun PluginData.Type.containsPath(): Boolean = when(this) {
    is PluginData.Type.ListType -> elementType.containsPath()
    is PluginData.Type.MapType -> valueType.containsPath()
    is PluginData.Type.ObjectType -> resolver.getClassData(this).properties.any { it.type.containsPath() }
    is PluginData.Type.PathType -> true
    else -> false
}