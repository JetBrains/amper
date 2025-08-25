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
import org.jetbrains.amper.frontend.tree.Refined
import org.jetbrains.amper.frontend.tree.RefinedTree
import org.jetbrains.amper.frontend.tree.ScalarValue
import org.jetbrains.amper.frontend.tree.TreeValue
import org.jetbrains.amper.frontend.types.SchemaObjectDeclaration
import org.jetbrains.amper.frontend.types.SchemaType
import org.jetbrains.amper.frontend.types.getType
import org.jetbrains.amper.frontend.types.toType
import org.jetbrains.amper.problems.reporting.ProblemReporter

/**
 * Instantiate requested [T] and fill its properties from the [node].
 * NOTE: if anything is reported in [MissingPropertiesHandler.onMissingRequiredPropertyValue], the resulting instance
 *  may throw on required property access.
 *
 * @param missingPropertiesHandler provides a hook to report/handle missing required values.
 */
internal inline fun <reified T : SchemaNode> BuildCtx.createSchemaNode(
    node: RefinedTree,
    missingPropertiesHandler: MissingPropertiesHandler = MissingPropertiesHandler.Default(problemReporter),
): T {
    return context(missingPropertiesHandler) {
        createNode(types.getType<T>(), node) as T
    }
}

context(missingPropertiesHandler: MissingPropertiesHandler)
private fun createNode(
    type: SchemaType,
    value: TreeValue<*>,
    forProperty: SchemaObjectDeclaration.Property? = null,
    propertyKeyTrace: Trace? = null,
): Any? {
    if (value is NoValue) {
        if (!type.isMarkedNullable && forProperty?.default == null) {
            // null is not allowed - report
            with(missingPropertiesHandler) {
                if (forProperty != null) {
                    onMissingRequiredPropertyValue(
                        trace = value.trace,
                        keyName = forProperty.name,
                        keyTrace = propertyKeyTrace,
                    )
                } else {
                    check(propertyKeyTrace == null)
                    onMissingRequiredValue(value.trace)
                }
            }
        }
        return null
    }

    return when (type) {
        is SchemaType.ScalarType if (value is ScalarValue<*>) -> value.value
        is SchemaType.ListType if (value is ListValue<*>) -> value.children.map {
            createNode(type.elementType, it)
        }.filter {
            it != null || type.elementType.isMarkedNullable  // skip all the `null`s if the type is not nullable
        }
        is SchemaType.MapType if (value is Refined) -> value.children.associate {
            val key = if (type.keyType.isTraceableWrapped) TraceableString(it.key, it.kTrace) else it.key
            key to createNode(type.valueType, it.value)
        }.filterValues {
            it != null || type.valueType.isMarkedNullable  // skip all the `null`s if the type is not nullable
        }
        is SchemaType.ObjectType if (value is Refined) -> {
            val declaration = type.declaration
            declaration.createInstance().apply {
                @OptIn(InternalTraceSetter::class)
                setTrace(value.trace)
                declaration.properties.forEach { property ->
                    val mapLikePropertyValue = value.refinedChildren[property.name]
                    if (mapLikePropertyValue == null) {
                        // Property is not mentioned at all
                        if (property.default == null && !property.type.isMarkedNullable) {
                            missingPropertiesHandler.onMissingRequiredPropertyValue(value.trace, property.name, null)
                        }
                        return@forEach
                    }
                    valueHolders[property.name] = ValueHolder(
                        createNode(
                            type = property.type,
                            value = mapLikePropertyValue.value,
                            forProperty = property,
                            propertyKeyTrace = mapLikePropertyValue.kTrace,
                        ),
                        mapLikePropertyValue.value.trace,
                    )
                }
            }
        }
        is SchemaType.VariantType if (value is Refined) -> {
            check(value.type in type.declaration.variants) {
                "Type error: ${value.type} not among ${type.declaration.variants}"
            }
            createNode(value.type!!.toType(), value)
        }
        else -> {
            // If crashes, it's not caused by an invalid user input, but rather by a bug in tree post-processing.
            error("Type error: expected a `$type`, got: `$value`")
        }
    }
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
     * @param keyName property name for which the value is missing.
     */
    fun onMissingRequiredPropertyValue(
        trace: Trace,
        keyName: String,
        keyTrace: Trace?,
    )

    /**
     * Called when a value is missing/`null` in a contexts where it's not a property's value.
     * Examples of such cases would be an element in a list or a map:
     * ```yaml
     * list: # List<String>
     *   - hello
     *   - # no value
     * ```
     * or
     * ```yaml
     * map: # Map<String, String>
     *   key1: value1
     *   key2: # no-value
     * ```
     *
     * @param trace a trace of the outermost yet present construct.
     *   in the list example above, it would point to the `-` symbol;
     *   in the map example above, it would point to the `key2:` construct.
     */
    fun onMissingRequiredValue(trace: Trace) {}

    /**
     * Default [MissingPropertiesHandler] implementation that does non-specific reporting.
     */
    open class Default(val problemReporter: ProblemReporter) : MissingPropertiesHandler {
        override fun onMissingRequiredPropertyValue(
            trace: Trace,
            keyName: String,
            keyTrace: Trace?,
        ) {
            problemReporter.reportBundleError(
                source = trace.asBuildProblemSource(),
                messageKey = "validation.missing.value",
                keyName,
            )
        }

        override fun onMissingRequiredValue(trace: Trace) {
            problemReporter.reportBundleError(
                source = trace.asBuildProblemSource(),
                messageKey = "validation.missing.value.free",
            )
        }
    }
}