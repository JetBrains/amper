/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.aomBuilder

import org.jetbrains.amper.frontend.api.Default
import org.jetbrains.amper.frontend.api.InternalTraceSetter
import org.jetbrains.amper.frontend.api.SchemaNode
import org.jetbrains.amper.frontend.api.Trace
import org.jetbrains.amper.frontend.api.TraceableString
import org.jetbrains.amper.frontend.api.ValueHolder
import org.jetbrains.amper.frontend.api.asTraceable
import org.jetbrains.amper.frontend.api.isDefault
import org.jetbrains.amper.frontend.asBuildProblemSource
import org.jetbrains.amper.frontend.reportBundleError
import org.jetbrains.amper.frontend.tree.BooleanNode
import org.jetbrains.amper.frontend.tree.EnumNode
import org.jetbrains.amper.frontend.tree.ErrorNode
import org.jetbrains.amper.frontend.tree.IntNode
import org.jetbrains.amper.frontend.tree.KeyValue
import org.jetbrains.amper.frontend.tree.NullLiteralNode
import org.jetbrains.amper.frontend.tree.PathNode
import org.jetbrains.amper.frontend.tree.ReferenceNode
import org.jetbrains.amper.frontend.tree.RefinedListNode
import org.jetbrains.amper.frontend.tree.RefinedMappingNode
import org.jetbrains.amper.frontend.tree.RefinedTreeNode
import org.jetbrains.amper.frontend.tree.StringInterpolationNode
import org.jetbrains.amper.frontend.tree.StringNode
import org.jetbrains.amper.frontend.tree.declaration
import org.jetbrains.amper.frontend.tree.enumConstantIfAvailable
import org.jetbrains.amper.frontend.types.SchemaObjectDeclaration
import org.jetbrains.amper.frontend.types.SchemaType
import org.jetbrains.amper.frontend.types.SchemaTypingContext
import org.jetbrains.amper.frontend.types.getType
import org.jetbrains.amper.frontend.types.isValueRequired
import org.jetbrains.amper.problems.reporting.ProblemReporter
import java.nio.file.Path

/**
 * Instantiate the requested [T] and fill its properties from the [node].
 *
 * If the object cannot be created because it is missing or incomplete, this function returns `null`.
 * This is the case when some required property is missing somewhere in the tree, for example.
 * This ensures all returned objects are complete and respect their contract.
 *
 * @param missingPropertiesHandler provides a hook to report/handle missing required values.
 */
context(problemReporter: ProblemReporter, types: SchemaTypingContext)
internal inline fun <reified T : SchemaNode> createSchemaNode(
    node: RefinedTreeNode,
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
context(problemReporter: ProblemReporter)
internal fun createSchemaNode(
    type: SchemaType,
    node: RefinedTreeNode,
    missingPropertiesHandler: MissingPropertiesHandler = MissingPropertiesHandler.Default(problemReporter),
): SchemaNode? {
    val node = context(missingPropertiesHandler) {
        createSchemaNode(type, node, valuePath = emptyList())
    }
    return if (node === ValueCreationErrorToken) null else node as SchemaNode
}

/**
 * A special sentinel value to represent conversion errors from tree values to real Kotlin values.
 *
 * It can be checked with referential equality to test for errors. This will be replaced with a proper error union in
 * the future when Kotlin has them, but in the meantime we can avoid the boilerplate of a result class and the extra
 * instantiations thanks to the `Any?` return type of [createSchemaNode].
 */
private object ValueCreationErrorToken

/**
 * A property path in a tree with associated value traces.
 * Example: `["settings" to <trace-1>, "kotlin" to <trace-2>, "version" to <trace-2>]`
 */
private typealias ValuePath = List<Pair<String, Trace>>

/**
 * Creates a value of the given [type] from the given tree [node].
 *
 * If the value cannot be created because it is missing or incomplete, this function returns a special
 * [ValueCreationErrorToken]. This ensures all objects are complete and respect their contract.
 *
 * @param missingPropertiesHandler provides a hook to report/handle missing required values.
 */
context(missingPropertiesHandler: MissingPropertiesHandler)
private fun createSchemaNode(
    type: SchemaType,
    node: RefinedTreeNode,
    valuePath: ValuePath,
): Any? {
    when (node) {
        is ErrorNode,
            // Do not report: reported during parsing
        is ReferenceNode, is StringInterpolationNode -> {
            // Do not report: must be already reported during reference resolution
            return ValueCreationErrorToken
        }
        else -> {}
    }

    return when (type) {
        is SchemaType.BooleanType if node is BooleanNode -> node.value
        is SchemaType.IntType if node is IntNode -> node.value
        is SchemaType.EnumType if node is EnumNode ->
            node.enumConstantIfAvailable?.wrapTraceable(type, node.trace) ?: node.entryName
        is SchemaType.StringType if node is StringNode -> node.value.wrapTraceable(type, node.trace)
        is SchemaType.PathType if node is PathNode -> node.value.wrapTraceable(type, node.trace)
        is SchemaType.ListType if node is RefinedListNode -> createListNode(node, type, valuePath)
        is SchemaType.MapType if node is RefinedMappingNode -> createMapNode(node, type, valuePath)
        is SchemaType.ObjectType if node is RefinedMappingNode -> createObjectNode(node, type, valuePath)
        is SchemaType.VariantType if node is RefinedMappingNode -> {
            check(node.declaration in type.declaration.variants) {
                "Type error: ${node.type} not among ${type.declaration.variants}"
            }
            createSchemaNode(node.type, node, valuePath)
        }
        else if (type.isMarkedNullable && node is NullLiteralNode) -> null
        else -> {
            // If crashes, it's not caused by an invalid user input, but rather by a bug in tree post-processing.
            error("Type error: expected a `$type`, got: `$node`")
        }
    }
}

context(_: MissingPropertiesHandler)
private fun createListNode(value: RefinedListNode, type: SchemaType.ListType, valuePath: ValuePath): List<Any?> =
    value.children
        .map { createSchemaNode(type.elementType, it, valuePath + ("[]" to it.trace)) }
        .filter { it !== ValueCreationErrorToken }

context(_: MissingPropertiesHandler)
private fun createMapNode(value: RefinedMappingNode, type: SchemaType.MapType, valuePath: ValuePath): Map<Any, Any?> =
    value.children
        .associate {
            val key = if (type.keyType.isTraceableWrapped) TraceableString(it.key, it.keyTrace) else it.key
            key to createSchemaNode(type.valueType, it.value, valuePath + (it.key to it.value.trace))
        }
        .filterValues { it !== ValueCreationErrorToken }

context(missingPropertiesHandler: MissingPropertiesHandler)
private fun createObjectNode(node: RefinedMappingNode, type: SchemaType.ObjectType, valuePath: ValuePath): Any {
    val declaration = type.declaration
    val newInstance = declaration.createInstance()
    @OptIn(InternalTraceSetter::class)
    newInstance.trace = node.trace

    for (mapLikePropertyValue in node.refinedChildren.values) {
        propertyCheckTypeLevelIntegrity(declaration, mapLikePropertyValue)
    }

    // we track this but don't return early, so we can report everything
    var hasMissingRequiredProps = false
    for (property in declaration.properties) {
        val propertyValuePath = valuePath + (property.name to node.trace)
        val mapLikePropertyValue = node.refinedChildren[property.name]
        propertyCheckDefaultIntegrity(property, mapLikePropertyValue)
        if (mapLikePropertyValue == null) {
            // Property is not mentioned at all
            if (property.isValueRequired()) {
                // Find the last non-default trace in the path - that's the *base* trace for our missing value
                val baseTraceIndex = propertyValuePath.indexOfLast { (_, trace) -> !trace.isDefault }
                val baseTrace = propertyValuePath[baseTraceIndex].second

                missingPropertiesHandler.onMissingRequiredPropertyValue(
                    trace = baseTrace,
                    valuePath = propertyValuePath.map { (name, _) -> name },
                    relativeValuePath = propertyValuePath.drop(baseTraceIndex).map { (name, _) -> name },
                )
                hasMissingRequiredProps = true
            }
            continue
        }
        val schemaNode = createSchemaNode(
            type = property.type,
            node = mapLikePropertyValue.value,
            valuePath = propertyValuePath,
        )
        if (schemaNode === ValueCreationErrorToken) {
            if (property.isValueRequired()) {
                // we don't report here because the error must have been reported when parsing the property
                hasMissingRequiredProps = true
            }
            continue
        }
        newInstance.valueHolders[property.name] = ValueHolder(
            value = schemaNode,
            valueTrace = mapLikePropertyValue.value.trace,
            keyValueTrace = mapLikePropertyValue.trace,
        )
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
     * @param trace a trace of the outermost explicitly present (non-default) construct.
     * @param valuePath a path (from the document root) for which the value is missing.
     *   E.g., `["product", "type"]` or `["repositories", "[]", "url"]"`
     * @param relativeValuePath a path relative to the construct with the [trace].
     *   E.g., when the [valuePath] is `["tasks", "myTask", "action", "classpath", "dependencies"]`,
     *   and the [trace] points to the `"action"` object,
     *   then the `relativeValuePath` is `["classpath", "dependencies"]`.
     */
    fun onMissingRequiredPropertyValue(
        trace: Trace,
        valuePath: List<String>,
        relativeValuePath: List<String>,
    )

    /**
     * Default [MissingPropertiesHandler] implementation that does non-specific reporting.
     */
    open class Default(val problemReporter: ProblemReporter) : MissingPropertiesHandler {
        override fun onMissingRequiredPropertyValue(
            trace: Trace,
            valuePath: List<String>,
            relativeValuePath: List<String>,
        ) {
            problemReporter.reportBundleError(
                source = trace.asBuildProblemSource(),
                messageKey = "validation.missing.value",
                relativeValuePath.joinToString("."),
            )
        }
    }
}

private fun propertyCheckDefaultIntegrity(
    pType: SchemaObjectDeclaration.Property,
    pValue: KeyValue?,
) {
    val hasTraceableDefault = pType.default != null && pType.default !is Default.TransformedDependent<*, *>
    check(!hasTraceableDefault || pValue != null) {
        "A property ${pType.name} has a traceable default ${pType.default}, " +
                "but the value is missing nevertheless. " +
                "This is a sign that the default was not properly merged on the tree level. " +
                "Please check that defaults are correctly appended for this tree."
    }
}

private fun propertyCheckTypeLevelIntegrity(
    declaration: SchemaObjectDeclaration,
    pValue: KeyValue,
) {
    val typeProperty = checkNotNull(pValue.propertyDeclaration) {
        "Property `${pValue.key}` is present on the tree value level, " +
                "but no corresponding property is defined in $declaration. " +
                "In case of extensible/synthetic declarations, " +
                "please ensure that additional properties are correctly represented on the type level. " +
                "Failing to do may break correct reference resolution/tooling, etc."
    }

    val expectedTypeProperty = declaration.getProperty(pValue.key)
    check(expectedTypeProperty == typeProperty) {
        "$declaration has property $expectedTypeProperty, but the value declares that it has $typeProperty. " +
                "They do not match. Something went wrong!"
    }
}

private fun Enum<*>.wrapTraceable(type: SchemaType.EnumType, trace: Trace) =
    if (type.isTraceableWrapped) asTraceable(trace) else this

private fun Path.wrapTraceable(type: SchemaType.PathType, trace: Trace) =
    if (type.isTraceableWrapped) asTraceable(trace) else this

private fun String.wrapTraceable(type: SchemaType.StringType, trace: Trace) =
    if (type.isTraceableWrapped) asTraceable(trace) else this