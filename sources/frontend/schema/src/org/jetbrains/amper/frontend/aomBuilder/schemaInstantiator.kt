/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.aomBuilder

import org.jetbrains.amper.frontend.api.InternalTraceSetter
import org.jetbrains.amper.frontend.api.SchemaNode
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
 */
internal inline fun <reified T : SchemaNode> BuildCtx.createSchemaNode(node: RefinedTree) = context(problemReporter) {
    createNode(types.getType<T>(), node) as T
}

context(problemReporter: ProblemReporter)
private fun createNode(
    type: SchemaType,
    currentValue: TreeValue<*>,
    forProperty: SchemaObjectDeclaration.Property? = null,
): Any? {
    if (currentValue is NoValue) {
        if (!type.isMarkedNullable && forProperty?.default == null) {
            // null is not allowed - report
            if (forProperty != null) {
                problemReporter.reportBundleError(
                    source = currentValue.trace.asBuildProblemSource(),
                    messageKey = "validation.missing.value",
                    forProperty.name,
                )
            } else {
                problemReporter.reportBundleError(
                    source = currentValue.trace.asBuildProblemSource(),
                    messageKey = "validation.missing.value.free",
                )
            }
        }
        return null
    }

    when (type) {
        is SchemaType.ScalarType -> if (currentValue is ScalarValue<*>)
            return currentValue.value
        is SchemaType.ListType -> if (currentValue is ListValue<*>)
            return currentValue.children.map { createNode(type.elementType, it) }
        is SchemaType.MapType -> if (currentValue is Refined) {
            return currentValue.children.associate {
                val key = if (type.keyType.isTraceableWrapped) TraceableString(it.key, it.kTrace) else it.key
                key to createNode(type.valueType, it.value)
            }
        }
        is SchemaType.ObjectType -> if (currentValue is Refined) {
            return type.declaration.createInstance().apply {
                @OptIn(InternalTraceSetter::class)
                setTrace(currentValue.trace)
                type.declaration.properties.forEach { property ->
                    val treeValue = currentValue.refinedChildren[property.name]?.value ?: return@forEach
                    val value = createNode(property.type, treeValue, forProperty = property)
                    valueHolders[property.name] = ValueHolder(value, treeValue.trace)
                }
            }
        }
        is SchemaType.VariantType -> {
            check(currentValue is Refined) { "Not reached: not Refined" }
            check(currentValue.type in type.declaration.variants) {
                "Type error: ${currentValue.type} not among ${type.declaration.variants}"
            }
            return createNode(currentValue.type!!.toType(), currentValue)
        }
    }

    error("Type error: expected a `$type`, got: `$currentValue`")
}