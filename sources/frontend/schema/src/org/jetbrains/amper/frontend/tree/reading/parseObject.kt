/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.tree.reading

import org.jetbrains.amper.frontend.api.asTrace
import org.jetbrains.amper.frontend.api.trace
import org.jetbrains.amper.frontend.contexts.Contexts
import org.jetbrains.amper.frontend.contexts.EmptyContexts
import org.jetbrains.amper.frontend.contexts.PlatformCtx
import org.jetbrains.amper.frontend.contexts.TestCtx
import org.jetbrains.amper.frontend.diagnostics.UnknownProperty
import org.jetbrains.amper.frontend.tree.MapLikeValue
import org.jetbrains.amper.frontend.tree.Owned
import org.jetbrains.amper.frontend.tree.ScalarValue
import org.jetbrains.amper.frontend.tree.copy
import org.jetbrains.amper.frontend.types.SchemaObjectDeclaration
import org.jetbrains.amper.frontend.types.SchemaType
import org.jetbrains.amper.frontend.types.SchemaType.Companion.KeyStringType
import org.jetbrains.amper.frontend.types.render
import org.jetbrains.amper.problems.reporting.ProblemReporter
import org.jetbrains.yaml.psi.YAMLKeyValue
import org.jetbrains.yaml.psi.YAMLMapping
import org.jetbrains.yaml.psi.YAMLScalar
import org.jetbrains.yaml.psi.YAMLSequence
import org.jetbrains.yaml.psi.YAMLValue

context(_: Contexts, _: ParsingConfig, _: ProblemReporter)
internal fun parseObject(
    psi: YAMLValue,
    type: SchemaType.ObjectType,
    allowTypeTag: Boolean = false,
): Owned? {
    if (!allowTypeTag) reportTypeTag(psi)

    val fromKeyProperty = type.declaration.getFromKeyAndTheRestNestedProperty()
    return if (fromKeyProperty != null) {
        parseObjectWithFromKeyProperty(fromKeyProperty, psi, type)
    } else {
        parseObjectWithoutFromKeyProperty(psi, type)
    }
}

context(_: Contexts, _: ParsingConfig, _: ProblemReporter)
private fun parseObjectWithFromKeyProperty(
    valueAsKeyProperty: SchemaObjectDeclaration.Property,
    psi: YAMLValue,
    type: SchemaType.ObjectType,
): Owned? {
    val argumentType = valueAsKeyProperty.type as SchemaType.ScalarType // should be scalar by design
    return when (psi) {
        is YAMLMapping -> {
            val argKeyValue = psi.keyValues.singleOrNull() ?: run {
                reportParsing(psi,  "validation.types.invalid.ctor.arg.key", type.render())
                return null
            }
            val key = YAMLScalarOrKey.parseKey(argKeyValue) ?: return null
            val argumentValue = parseScalar(key, argumentType)
                ?: return null
            val nestedRemainingObject = argKeyValue.value
            if (nestedRemainingObject == null) {
                reportParsing(argKeyValue, "validation.types.invalid.ctor.arg.value", type.render())
                return null
            }
            val remainingProperties = parseObjectWithoutFromKeyProperty(nestedRemainingObject, type)
                ?: return null
            remainingProperties.copy(
                children = listOf(
                    MapLikeValue.Property(
                        value = argumentValue,
                        kTrace = key.psi.asTrace(),
                        pType = valueAsKeyProperty,
                    )
                ) + remainingProperties.children,
                trace = argKeyValue.asTrace(),
            ) as Owned
        }
        is YAMLScalar -> mapLikeValue(
            children = listOf(
                MapLikeValue.Property(
                    value = parseValue(psi, argumentType) ?: return null,
                    kTrace = psi.asTrace(),
                    pType = valueAsKeyProperty,
                )
            ),
            origin = psi, type = type,
        )
        else -> {
            reportParsing(psi, "validation.types.invalid.ctor.arg", argumentType.render())
            null
        }
    }
}

context(_: Contexts, _: ParsingConfig, _: ProblemReporter)
private fun parseObjectWithoutFromKeyProperty(psi: YAMLValue, type: SchemaType.ObjectType): Owned? {
    return when (psi) {
        is YAMLMapping -> parseObjectFromMap(psi, type)
        is YAMLScalar -> parseObjectFromScalarShorthand(YAMLScalarOrKey(psi), type)
        is YAMLSequence -> parseObjectFromListShorthand(psi, type)
        else -> {
            reportParsing(psi, "validation.types.expected.object", formatValueForMessage(psi))
            null
        }
    }
}

context(contexts: Contexts, config: ParsingConfig, reporter: ProblemReporter)
private fun parseObjectFromMap(psi: YAMLMapping, type: SchemaType.ObjectType): Owned {
    fun parseObjectProperty(keyValue: YAMLKeyValue): MapLikeValue.Property<*>? {
        val key = YAMLScalarOrKey.parseKey(keyValue) ?: return null
        val (propertyName, propertyContexts) = parsePropertyKeyContexts(key)
            ?: return null
        val property = type.declaration.getProperty(propertyName)?.takeUnless { it.isFromKeyAndTheRestNested }
        if (property == null) {
            return if (!config.skipUnknownProperties) {
                reporter.reportMessage(
                    UnknownProperty(
                        invalidName = propertyName,
                        possibleIntendedNames = type.declaration.properties
                            .filter { propertyName in it.misnomers }
                            .map { it.name },
                        element = keyValue.key ?: keyValue,
                    )
                )
                null
            } else null
        }
        return MapLikeValue.Property(
            value = parseValueFromKeyValue(keyValue, property.type, explicitContexts = propertyContexts),
            kTrace = key.psi.asTrace(),
            pType = property,
        )
    }

    return mapLikeValue(
        children = psi.keyValues.mapNotNull { keyValue ->
            parseObjectProperty(keyValue)
        },
        origin = psi,
        type = type,
    )
}

context(_: Contexts, _: ParsingConfig, _: ProblemReporter)
private fun parseObjectFromScalarShorthand(
    scalar: YAMLScalarOrKey,
    type: SchemaType.ObjectType,
): Owned? {
    fun parseScalarShorthandValue(): Pair<SchemaObjectDeclaration.Property, ScalarValue<*>?>? {
        val boolean = type.declaration.getBooleanShorthand()
        val secondary = type.declaration.getSecondaryShorthand()

        if (boolean != null && scalar.textValue == boolean.name) {
            return boolean to scalarValue(scalar, true)
        }
        when (val type = secondary?.type) {
            is SchemaType.ScalarType -> return secondary to parseScalar(scalar, type)
            else -> Unit
        }
        return null
    }

    val (property, result) = parseScalarShorthandValue() ?: run {
        reportParsing(scalar.psi, "validation.types.expected.object", formatValueForMessage(scalar))
        return null
    }

    val value = result ?: return null

    return mapLikeValue(
        children = listOf(
            MapLikeValue.Property(
                value = value,
                kTrace = scalar.psi.asTrace(),
                pType = property,
            )
        ),
        type = type,
        origin = scalar.psi,
    )
}

context(_: Contexts, _: ParsingConfig, _: ProblemReporter)
private fun parseObjectFromListShorthand(
    psi: YAMLSequence,
    type: SchemaType.ObjectType,
): Owned? {
    val listShorthandProperty = type.declaration.getSecondaryShorthand()?.takeIf { it.type is SchemaType.ListType }

    if (listShorthandProperty != null) {
        val propertyType = listShorthandProperty.type as SchemaType.ListType
        // At this point we are committed to read this as a shorthand, so
        return mapLikeValue(
            children = listOfNotNull(
                MapLikeValue.Property(
                    value = parseList(psi, propertyType),
                    kTrace = psi.trace,
                    pType = listShorthandProperty,
                )
            ),
            type = type, origin = psi,
        )
    }
    // shorthand is unsupported
    reportParsing(psi, "validation.types.expected.object")
    return null
}

context(_: Contexts, config: ParsingConfig, _: ProblemReporter)
private fun parsePropertyKeyContexts(
    key: YAMLScalarOrKey,
): Pair<String, Contexts>? {
    val keyText = context(EmptyContexts) {
        parseScalar(key, KeyStringType) ?: return null
    }.value as String
    if (config.supportContexts) {
        val keyWithoutContext = keyText.substringBefore('@')
        val context = if (keyWithoutContext === keyText) null else keyText.substringAfter('@')
        if (context != null && '+' in context) {
            reportParsing(key.psi, "multiple.qualifiers.are.unsupported")
            return null
        }
        val (mappedName, requiresTestContext) = when (keyWithoutContext) {
            "test-settings" -> "settings" to true
            "test-dependencies" -> "dependencies" to true
            else -> keyWithoutContext to false
        }
        val trace = key.psi.asTrace()
        return mappedName to buildSet {
            if (requiresTestContext) add(TestCtx(trace))
            if (context != null) add(PlatformCtx(context, trace))
        }
    } else {
        // TODO: Reserve context syntax for the future?
        return keyText to EmptyContexts
    }
}
