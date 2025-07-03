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
import org.jetbrains.amper.frontend.messages.extractPsiElementOrNull
import org.jetbrains.amper.frontend.types.SchemaTypingContext
import org.jetbrains.amper.frontend.tree.ListValue
import org.jetbrains.amper.frontend.tree.MapLikeValue
import org.jetbrains.amper.frontend.tree.Refined
import org.jetbrains.amper.frontend.tree.RefinedTree
import org.jetbrains.amper.frontend.tree.ScalarValue
import org.jetbrains.amper.frontend.tree.TreeValueReporterCtx
import org.jetbrains.amper.frontend.types.SchemaObjectDeclaration
import org.jetbrains.amper.frontend.types.SchemaType
import org.jetbrains.amper.frontend.types.getType
import org.jetbrains.amper.frontend.types.isValueRequired
import org.jetbrains.amper.frontend.types.nameAndAliases
import org.jetbrains.amper.frontend.types.toType
import kotlin.reflect.full.createInstance

/**
 * Instantiate requested [T] and fill its properties from the [node].
 */
internal inline fun <reified T : SchemaNode> BuildCtx.createSchemaNode(node: RefinedTree) =
    InstantiationCtx(this, types, types.getType<T>(), node).createSchemaNode() as T

internal class InstantiationCtx<V : RefinedTree, T : SchemaType>(
    private val reporterCtx: ProblemReporterContext,
    private val types: SchemaTypingContext,
    val currentType: T,
    val currentValue: V,
    val parents: List<RefinedTree> = emptyList(),
) : ProblemReporterContext by reporterCtx, TreeValueReporterCtx {

    /**
     * Create new factory with new class.
     */
    private fun <N : SchemaType> newCtx(type: N) = InstantiationCtx(reporterCtx, types, type, currentValue, parents)

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
        val declaration = currentType.declaration
        declaration.properties.forEach { prop ->
            val child = prop.nameAndAliases().firstNotNullOfOrNull { currentValue.asMap[it] }
            if (child == null && prop.isValueRequired()) {
                currentValue.reportAndNull(
                    problemId = "validation.missing.value",
                    prop.name,
                )
                error("No node for prop ${prop.name}")
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
    private fun SchemaNode.setSafely(prop: SchemaObjectDeclaration.Property, value: Any) {
        if (value is String && prop.type is SchemaType.StringType &&
            prop.knownStringValues.isNotEmpty() && value !in prop.knownStringValues
        ) {
            currentValue.reportAndNull(
                problemId = "validation.not.within.known.values",
                prop.knownStringValues.joinToString()
            )
        }
        val currentPsi = currentValue.psiElement
        val newState = when {
            currentPsi == null -> ValueState.EXPLICIT
            // We had to merge; therefore, this node has its own value.
            currentValue.trace.precedingValue != null -> ValueState.MERGED

            parents.any { it.trace.precedingValue != null } -> ValueState.INHERITED
            // If none of above conditions are met then node is explicitly set.
            else -> ValueState.EXPLICIT
        }

        valueHolders[prop.name] = ValueHolder(value, newState, currentValue.trace)
    }

    /**
     * Tries to instantiate [currentType] with default constructor
     * or choose right descendant if [currentType] is polymorphic.
     */
    private fun tryInstantiate(
        configure: InstantiationCtx<MapLikeValue<Refined>, SchemaType.ObjectType>.(SchemaNode) -> Unit
    ): SchemaNode {
        if (currentValue !is MapLikeValue<*>)
            error("Expected `MapValue` but got `$currentValue` instead.") // TODO Report.

        val valueType = currentValue.type
        when (currentType) {
            is SchemaType.ObjectType -> if (valueType !== currentType.declaration)
                error("Expected value to have `$currentType` but got `$valueType` instead.")
            is SchemaType.VariantType -> if (valueType !in currentType.declaration.variants)
                error("Expected value type (`$valueType`) to be inheritor of `$currentType`.")
            else -> error("Expected `AObject` or `APolymorphic` but got `$currentType` instead.")
        }

        val kClass = checkNotNull(valueType).backingReflectionClass ?: SchemaNode::class
        return kClass.createInstance().also {
            // We can safely cast here, because [currentValue] is [MapValue], but smart cast cannot
            // raise type deduction to the class from property.
            @Suppress("UNCHECKED_CAST")
            (newCtx(valueType.toType()) as InstantiationCtx<MapLikeValue<Refined>, SchemaType.ObjectType>).configure(it)
        }
    }

    /**
     * Read value of a specified [type] from the children of the [currentValue].
     */
    private fun readValue(type: SchemaType): Any? = when (type) {
        is SchemaType.MapType -> currentValue.asSafely<MapLikeValue<Refined>>()
            .also { if (it == null) currentValue.reportAndNull("validation.expected.map") }
            ?.children?.associate {
                val key = if (type.keyType.isTraceableWrapped) {
                    TraceableString(it.key).apply { trace = it.kTrace }
                } else it.key
                key to it.value.newCtx().readValue(type.valueType)
            }

        is SchemaType.ListType -> currentValue.asSafely<ListValue<Refined>>()
            .also { if (it == null) currentValue.reportAndNull("validation.expected.collection") }
            ?.children?.map { it.newCtx().readValue(type.elementType) }

        // Reporting should happen when we are reading the tree.
        is SchemaType.ScalarType -> currentValue.asSafely<ScalarValue<Refined>>()?.value

        // Reporting will happen in [createSchemaNode].
        is SchemaType.ObjectType, is SchemaType.VariantType -> newCtx(type).createSchemaNode()
    }

    // We can use this transformation here because we treat the tree as already merged,
    // so we are not expecting any duplicating keys.
    private val MapLikeValue<*>.asMap: Map<String, RefinedTree> get() = children.associate { it.key to it.value as RefinedTree }
    private val RefinedTree.psiElement get() = trace.extractPsiElementOrNull()
}