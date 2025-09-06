/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.aomBuilder

import org.jetbrains.amper.frontend.api.InternalTraceSetter
import org.jetbrains.amper.frontend.api.SchemaNode
import org.jetbrains.amper.frontend.api.Trace
import org.jetbrains.amper.frontend.api.TraceableString
import org.jetbrains.amper.frontend.api.ValueHolder
import org.jetbrains.amper.frontend.asBuildProblemSource
import org.jetbrains.amper.frontend.reportBundleError
import org.jetbrains.amper.frontend.tree.ListValue
import org.jetbrains.amper.frontend.tree.NoValue
import org.jetbrains.amper.frontend.tree.NullValue
import org.jetbrains.amper.frontend.tree.Refined
import org.jetbrains.amper.frontend.tree.RefinedTree
import org.jetbrains.amper.frontend.tree.ScalarValue
import org.jetbrains.amper.frontend.tree.TreeValue
import org.jetbrains.amper.frontend.types.SchemaObjectDeclaration
import org.jetbrains.amper.frontend.types.SchemaType
import org.jetbrains.amper.frontend.types.getType
import org.jetbrains.amper.frontend.types.isValueRequired
import org.jetbrains.amper.frontend.types.toType
import org.jetbrains.amper.problems.reporting.ProblemReporter

/**
 * Instantiate the requested [T] and fill its properties from the [node].
 *
 * If the object cannot be created because it is missing or incomplete, this function returns `null`.
 * This is the case when some required property is missing somewhere in the tree, for example.
 * This ensures all returned objects are complete and respect their contract.
 *
 * @param missingPropertiesHandler provides a hook to report/handle missing required values.
 */
internal inline fun <reified T : SchemaNode> BuildCtx.createSchemaNode(
    node: RefinedTree,
    missingPropertiesHandler: MissingPropertiesHandler = MissingPropertiesHandler.Default(problemReporter),
): T? = createSchemaNode(types.getType<T>(), node, missingPropertiesHandler) as T?

/**
 * Creates a new [SchemaNode] of the given [type] from the given tree [node].
 *
 * If the object cannot be created because it is missing or incomplete, this function returns `null`.
 * This is the case when some required property is missing somewhere in the tree, for example.
 * This ensures all returned objects are complete and respect their contract.
 *
 * @param missingPropertiesHandler provides a hook to report/handle missing required values.
 */
internal fun BuildCtx.createSchemaNode(
    type: SchemaType,
    node: TreeValue<*>,
    missingPropertiesHandler: MissingPropertiesHandler = MissingPropertiesHandler.Default(problemReporter),
): SchemaNode? {
    val node = context(missingPropertiesHandler) {
        createNode(type, node, valuePath = emptyList())
    }
    return if (node === ValueCreationErrorToken) null else node as SchemaNode
}

/**
 * A special sentinel value to represent conversion errors from tree values to real Kotlin values.
 *
 * It can be checked with referential equality to test for errors. This will be replaced with a proper error union in
 * the future when Kotlin has them, but in the meantime we can avoid the boilerplate of a result class and the extra
 * instantiations thanks to the `Any?` return type of [createNode].
 */
private object ValueCreationErrorToken

/**
 * Creates a value of the given [type] from the given tree [value].
 *
 * If the value cannot be created because it is missing or incomplete, this function returns a special
 * [ValueCreationErrorToken]. This ensures all objects are complete and respect their contract.
 *
 * @param missingPropertiesHandler provides a hook to report/handle missing required values.
 */
context(missingPropertiesHandler: MissingPropertiesHandler)
private fun createNode(
    type: SchemaType,
    value: TreeValue<*>,
    valuePath: List<String>,
    forProperty: SchemaObjectDeclaration.Property? = null,
    propertyKeyTrace: Trace? = null,
): Any? {
    if (value is NoValue) {
        if (forProperty?.isValueRequired() == true) {
            missingPropertiesHandler.onMissingRequiredPropertyValue(
                trace = value.trace,
                valuePath = valuePath,
                keyTrace = propertyKeyTrace,
            )
        }
        return ValueCreationErrorToken
    }

    return when (type) {
        is SchemaType.ScalarType if (value is ScalarValue<*>) -> value.value
        is SchemaType.ListType if (value is ListValue<*>) -> createListNode(value, type, valuePath)
        is SchemaType.MapType if (value is Refined) -> createMapNode(value, type, valuePath)
        is SchemaType.ObjectType if (value is Refined) -> createObjectNode(value, type, valuePath)
        is SchemaType.VariantType if (value is Refined) -> {
            check(value.type in type.declaration.variants) {
                "Type error: ${value.type} not among ${type.declaration.variants}"
            }
            createNode(value.type!!.toType(), value, valuePath)
        }
        else if (type.isMarkedNullable && value is NullValue<*>) -> null
        else -> {
            // If crashes, it's not caused by an invalid user input, but rather by a bug in tree post-processing.
            error("Type error: expected a `$type`, got: `$value`")
        }
    }
}

context(missingPropertiesHandler: MissingPropertiesHandler)
private fun createListNode(value: ListValue<*>, type: SchemaType.ListType, valuePath: List<String>): List<Any?> =
    value.children
        .map { createNode(type.elementType, it, valuePath + "[]") }
        .filter { it !== ValueCreationErrorToken }

context(missingPropertiesHandler: MissingPropertiesHandler)
private fun createMapNode(value: Refined, type: SchemaType.MapType, valuePath: List<String>): Map<Any, Any?> =
    value.children
        .associate {
            val key = if (type.keyType.isTraceableWrapped) TraceableString(it.key, it.kTrace) else it.key
            key to createNode(type.valueType, it.value, valuePath + it.key)
        }
        .filterValues { it !== ValueCreationErrorToken }

context(missingPropertiesHandler: MissingPropertiesHandler)
private fun createObjectNode(value: Refined, type: SchemaType.ObjectType, valuePath: List<String>): Any {
    val declaration = type.declaration
    val newInstance = declaration.createInstance()
    @OptIn(InternalTraceSetter::class)
    newInstance.trace = value.trace

    // we track this but don't return early, so we can report everything
    var hasMissingRequiredProps = false
    for (property in declaration.properties) {
        val propertyValuePath = valuePath + property.name
        val mapLikePropertyValue = value.refinedChildren[property.name]
        if (mapLikePropertyValue == null) {
            // Property is not mentioned at all
            if (property.isValueRequired()) {
                missingPropertiesHandler.onMissingRequiredPropertyValue(
                    trace = value.trace,
                    valuePath = propertyValuePath,
                    keyTrace = null,
                )
                hasMissingRequiredProps = true
            }
            continue
        }
        val node = createNode(
            type = property.type,
            value = mapLikePropertyValue.value,
            forProperty = property,
            propertyKeyTrace = mapLikePropertyValue.kTrace,
            valuePath = propertyValuePath,
        )
        if (node === ValueCreationErrorToken) {
            if (property.isValueRequired()) {
                // we don't report here because the error must have been reported when parsing the property
                hasMissingRequiredProps = true
            }
            continue
        }
        newInstance.valueHolders[property.name] = ValueHolder(node, mapLikePropertyValue.value.trace)
    }
    if (hasMissingRequiredProps) {
        // We don't allow incomplete objects
        return ValueCreationErrorToken
    }
    return newInstance
}

/**
 * See [createSchemaNode].
 */
interface MissingPropertiesHandler {
    /**
     * Called when a value is missing for a "required" property.
     *
     * @param trace a trace of the outermost yet present construct.
     * @param keyTrace a trace of the property key specifically, if present, e.g. `required-key: # no-value`.
     *   If the property is simply omitted entirely, this is `null`.
     * @param valuePath a path for which the value is missing.
     *   E.g., `["product", "type"]` or `["repositories", "[]", "url"]"`
     */
    fun onMissingRequiredPropertyValue(
        trace: Trace,
        valuePath: List<String>,
        keyTrace: Trace?,
    )

    /**
     * Default [MissingPropertiesHandler] implementation that does non-specific reporting.
     */
    open class Default(val problemReporter: ProblemReporter) : MissingPropertiesHandler {
        override fun onMissingRequiredPropertyValue(
            trace: Trace,
            valuePath: List<String>,
            keyTrace: Trace?,
        ) {
            problemReporter.reportBundleError(
                source = trace.asBuildProblemSource(),
                messageKey = "validation.missing.value",
                valuePath.last(),
            )
        }
    }
}