/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.aomBuilder

import com.intellij.util.asSafely
import org.jetbrains.amper.core.messages.ProblemReporterContext
import org.jetbrains.amper.frontend.api.SchemaNode
import org.jetbrains.amper.frontend.api.Traceable
import org.jetbrains.amper.frontend.api.TraceableString
import org.jetbrains.amper.frontend.api.ValueHolder
import org.jetbrains.amper.frontend.api.ValueState
import org.jetbrains.amper.frontend.api.valueBase
import org.jetbrains.amper.frontend.types.isString
import org.jetbrains.amper.frontend.types.isSubclassOf
import org.jetbrains.amper.frontend.types.kClass
import org.jetbrains.amper.frontend.messages.extractPsiElementOrNull
import org.jetbrains.amper.frontend.types.AmperTypes
import org.jetbrains.amper.frontend.types.AmperTypes.List
import org.jetbrains.amper.frontend.types.AmperTypes.Map
import org.jetbrains.amper.frontend.types.AmperTypes.Object
import org.jetbrains.amper.frontend.types.AmperTypes.Polymorphic
import org.jetbrains.amper.frontend.types.AmperTypes.Scalar
import org.jetbrains.amper.frontend.types.AmperTypes.AmperType
import org.jetbrains.amper.frontend.tree.ListValue
import org.jetbrains.amper.frontend.tree.MapLikeValue
import org.jetbrains.amper.frontend.tree.Refined
import org.jetbrains.amper.frontend.tree.RefinedTree
import org.jetbrains.amper.frontend.tree.ScalarValue
import org.jetbrains.amper.frontend.tree.TreeValueReporterCtx
import org.jetbrains.amper.frontend.types.isTraceableString
import org.jetbrains.amper.frontend.types.mapKeyType
import kotlin.reflect.KFunction
import kotlin.reflect.KParameter
import kotlin.reflect.KProperty1


/**
 * Instantiate requested [T] and fill its properties from the [node].
 */
internal inline fun <reified T : SchemaNode> BuildCtx.createSchemaNode(node: RefinedTree) =
    InstantiationCtx(this, types, types<T>() as Object, node).createSchemaNode() as T

internal class InstantiationCtx<V : RefinedTree, T : AmperType>(
    private val reporterCtx: ProblemReporterContext,
    private val types: AmperTypes,
    val currentType: T,
    val currentValue: V,
    val parents: kotlin.collections.List<RefinedTree> = emptyList(),
) : ProblemReporterContext by reporterCtx, TreeValueReporterCtx {

    /**
     * Create new factory with new class.
     */
    private fun <N : AmperType> newCtx(type: N) = InstantiationCtx(reporterCtx, types, type, currentValue, parents)

    /**
     * Create new factory with new current node.
     */
    private fun <N : RefinedTree> N.newCtx(block: InstantiationCtx<N, T>.() -> Unit = {}) =
        InstantiationCtx(reporterCtx, types, currentType, this, parents + currentValue).apply(block)

    /**
     * Create [currentType] instance and fill its properties from [currentValue].
     */
    fun createSchemaNode(): Any = tryInstantiate { instance ->
        instance.asSafely<Traceable>()?.trace = currentValue.trace
        currentType.properties.forEach { prop ->
            val delegate = prop.meta.kProperty.asSafely<KProperty1<Any, *>>()?.valueBase(instance)
            val child = prop.meta.nameAndAliases.firstNotNullOfOrNull { currentValue.asMap[it] }
            if (child == null && delegate?.isValueRequired == true) {
                currentValue.reportAndNull(
                    problemId = "validation.missing.value",
                    delegate.name,
                )
                error("No node for prop ${delegate.property}")
            } else child?.newCtx {
                // When we can't read a value - then validation was not successful for it,
                // therefore, an error is already reported, and we should skip, or that
                // value is from some custom schema.
                instance.setSafely(prop, readValue(prop.type) ?: return@newCtx)
            }
        }
    }

    /**
     * Try to perform last validations on the property value and then set it with the correct trace.
     */
    private fun SchemaNode.setSafely(prop: AmperTypes.Property, value: Any) {
        if (value is String && prop.meta.type.isString && prop.meta.knownStringValues?.contains(value) == false)
            currentValue.reportAndNull(
                problemId = "validation.not.within.known.values",
                prop.meta.knownStringValues!!.joinToString()
            )
        val currentPsi = currentValue.psiElement
        val newState = when {
            currentPsi == null -> ValueState.EXPLICIT
            // We had to merge; therefore, this node has its own value.
            currentValue.trace.precedingValue != null -> ValueState.MERGED

            parents.any { it.trace.precedingValue != null } -> ValueState.INHERITED
            // If none of above conditions are met then node is explicitly set.
            else -> ValueState.EXPLICIT
        }

        valueHolders[prop.meta.name] = ValueHolder(value, newState, currentValue.trace)
    }

    /**
     * Tries to instantiate [currentType] with default constructor
     * or choose right descendant if [currentType] is polymorphic.
     */
    private fun tryInstantiate(configure: InstantiationCtx<MapLikeValue<Refined>, Object>.(SchemaNode) -> Unit): SchemaNode {
        if (currentValue !is MapLikeValue<*>)
            error("Expected `MapValue` but got `$currentValue` instead.") // TODO Report.

        val valueType = currentValue.type
        when (currentType) {
            is Object -> if (valueType !== currentType) error("Expected value to have `$currentType` but got `$valueType` instead.")
            is Polymorphic -> if (valueType !in currentType.inheritors) error("Expected value type (`$valueType`) to be inheritor of `$currentType`.")
            else -> error("Expected `AObject` or `APolymorphic` but got `$currentType` instead.")
        }

        fun KParameter.valuePresent() = isOptional || type.isMarkedNullable || currentValue.asMap.containsKey(name)
        val matchingCtor = valueType!!.kType.kClass.constructors
            .filterIsInstance<KFunction<SchemaNode>>()
            .filter { it.returnType.kClass.isSubclassOf(SchemaNode::class) }
            .singleOrNull { it.parameters.all(KParameter::valuePresent) }
            ?: error("No matching constructors found for `$currentValue` of type `$valueType`") // TODO Replace with reporting.

        val builtParameters = matchingCtor.parameters
            .associateWith { currentValue.asMap[it.name]?.asSafely<ScalarValue<Refined>>() } // TODO Report non scalar parameters.
            // We should not pass optional parameters for which we don't have any value.
            .filterNot { it.value == null && it.key.isOptional }
            .mapValues { it.value?.value }

        return matchingCtor.callBy(builtParameters).also {
            // We can safely cast here, because [currentValue] is [MapValue], but smart cast cannot
            // raise type deduction to the class from property.
            @Suppress("UNCHECKED_CAST")
            (newCtx(valueType) as InstantiationCtx<MapLikeValue<Refined>, Object>).configure(it)
        }
    }

    /**
     * Read value of a specified [type] from the children of the [currentValue].
     */
    private fun readValue(type: AmperType): Any? = when (type) {
        is Map -> currentValue.asSafely<MapLikeValue<Refined>>()
            .also { if (it == null) currentValue.reportAndNull("validation.expected.map") }
            ?.children?.associate {
                val key = if (type.kType.mapKeyType.isTraceableString) {
                    TraceableString(it.key).apply { trace = it.kTrace }
                } else it.key
                key to it.value.newCtx().readValue(type.valueType)
            }

        is List -> currentValue.asSafely<ListValue<Refined>>()
            .also { if (it == null) currentValue.reportAndNull("validation.expected.collection") }
            ?.children?.map { it.newCtx().readValue(type.valueType) }

        // Reporting should happen when we are reading the tree.
        is Scalar -> currentValue.asSafely<ScalarValue<Refined>>()?.value

        // Reporting will happen in [createSchemaNode].
        is Object, is Polymorphic -> newCtx(type).createSchemaNode()
    }

    // We can use this transformation here because we treat the tree as already merged,
    // so we are not expecting any duplicating keys.
    private val MapLikeValue<*>.asMap: kotlin.collections.Map<String, RefinedTree> get() = children.associate { it.key to it.value as RefinedTree }
    private val RefinedTree.psiElement get() = trace.extractPsiElementOrNull()
}