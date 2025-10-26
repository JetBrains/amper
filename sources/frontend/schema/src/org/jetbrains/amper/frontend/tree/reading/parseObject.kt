/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.tree.reading

import org.jetbrains.amper.frontend.api.asTrace
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
import org.jetbrains.amper.frontend.types.render
import org.jetbrains.amper.problems.reporting.ProblemReporter

context(_: Contexts, _: ParsingConfig, _: ProblemReporter)
internal fun parseObject(
    value: YamlValue,
    type: SchemaType.ObjectType,
    allowTypeTag: Boolean = false,
): Owned? {
    if (!allowTypeTag) {
        value.tag?.let { tag ->
            if (!tag.text.startsWith("!!")) {  // Standard "!!" tags are reported in `parseValue`
                reportParsing(tag, "validation.structure.unsupported.tag")
            }
        }
    }

    val fromKeyProperty = type.declaration.getFromKeyAndTheRestNestedProperty()
    return if (fromKeyProperty != null) {
        parseObjectWithFromKeyProperty(fromKeyProperty, value, type)
    } else {
        parseObjectWithoutFromKeyProperty(value, type)
    }
}

context(_: Contexts, _: ParsingConfig, _: ProblemReporter)
private fun parseObjectWithFromKeyProperty(
    valueAsKeyProperty: SchemaObjectDeclaration.Property,
    value: YamlValue,
    type: SchemaType.ObjectType,
): Owned? {
    val argumentType = valueAsKeyProperty.type as SchemaType.ScalarType // should be scalar by design
    return when (value) {
        is YamlValue.Mapping -> {
            val argKeyValue = value.keyValues.singleOrNull() ?: run {
                reportParsing(value, "validation.types.invalid.ctor.arg.key", type.render())
                return null
            }
            val argumentValue = parseScalarKey(argKeyValue.key, argumentType)
                ?: return null
            val nestedRemainingObject = argKeyValue.value
            val remainingProperties = parseObjectWithoutFromKeyProperty(nestedRemainingObject, type)
                ?: return null
            remainingProperties.copy(
                children = listOf(
                    MapLikeValue.Property(
                        value = argumentValue,
                        kTrace = argKeyValue.key.asTrace(),
                        pType = valueAsKeyProperty,
                    )
                ) + remainingProperties.children,
                trace = argKeyValue.asTrace(),
            ) as Owned
        }
        is YamlValue.Scalar -> mapLikeValue(
            children = listOf(
                MapLikeValue.Property(
                    value = parseValue(value, argumentType) ?: return null,
                    kTrace = value.asTrace(),
                    pType = valueAsKeyProperty,
                )
            ),
            origin = value, type = type,
        )
        else -> {
            // `renderOnlyNestedTypeSyntax` is needed to include the `(prop | prop: (...))` syntax.
            reportUnexpectedValue(value, type, renderOnlyNestedTypeSyntax = false)
            null
        }
    }
}

context(_: Contexts, _: ParsingConfig, _: ProblemReporter)
private fun parseObjectWithoutFromKeyProperty(value: YamlValue, type: SchemaType.ObjectType): Owned? {
    return when (value) {
        is YamlValue.Mapping -> parseObjectFromMap(value, type)
        is YamlValue.Scalar -> parseObjectFromScalarShorthand(value, type)
        is YamlValue.Sequence -> parseObjectFromListShorthand(value, type)
        else -> {
            reportUnexpectedValue(value, type)
            null
        }
    }
}

context(contexts: Contexts, config: ParsingConfig, reporter: ProblemReporter)
private fun parseObjectFromMap(value: YamlValue.Mapping, type: SchemaType.ObjectType): Owned {
    fun parseObjectProperty(keyValue: YamlKeyValue): MapLikeValue.Property<*>? {
        val key = keyValue.key
        val (propertyName, propertyContexts) = parsePropertyKeyContexts(key)
            ?: return null
        val property = type.declaration.getProperty(propertyName)
            ?.takeUnless { it.isFromKeyAndTheRestNested }
        if (property == null) {
            return if (!config.skipUnknownProperties) {
                reporter.reportMessage(
                    UnknownProperty(
                        invalidName = propertyName,
                        possibleIntendedNames = type.declaration.properties
                            .filter { propertyName in it.misnomers }
                            .map { it.name },
                        element = key.psi,
                    )
                )
                null
            } else null
        }
        if (!property.isUserSettable) {
            reportParsing(key, "validation.property.not.settable", property.name)
            return null
        }
        return MapLikeValue.Property(
            value = parseValueFromKeyValue(keyValue, property.type, explicitContexts = propertyContexts),
            kTrace = key.asTrace(),
            pType = property,
        )
    }

    return mapLikeValue(
        children = value.keyValues.mapNotNull { keyValue ->
            parseObjectProperty(keyValue)
        },
        origin = value,
        type = type,
    )
}

context(_: Contexts, _: ParsingConfig, _: ProblemReporter)
private fun parseObjectFromScalarShorthand(
    scalar: YamlValue.Scalar,
    type: SchemaType.ObjectType,
): Owned? {
    fun parseScalarShorthandValue(): Pair<SchemaObjectDeclaration.Property, ScalarValue<*>?>? {
        val boolean = type.declaration.getBooleanShorthand()
        val secondary = type.declaration.getSecondaryShorthand()

        if (boolean != null && scalar.textValue == boolean.name) {
            return boolean to scalarValue(scalar, SchemaType.BooleanType, true)
        }
        return when (val type = secondary?.type) {
            is SchemaType.EnumType -> secondary to parseEnum(
                // additionalSuggestedValues is specified
                // to include the boolean shorthand in the report if parsing fails.
                scalar, type, additionalSuggestedValues = listOfNotNull(boolean?.name),
            )
            is SchemaType.ScalarType -> secondary to parseScalar(scalar, type)
            else -> null
        }
    }

    val (property, result) = parseScalarShorthandValue() ?: run {
        reportUnexpectedValue(scalar, type)
        return null
    }

    val value = result ?: return null

    return mapLikeValue(
        children = listOf(
            MapLikeValue.Property(
                value = value,
                kTrace = scalar.asTrace(),
                pType = property,
            )
        ),
        type = type,
        origin = scalar,
    )
}

context(_: Contexts, _: ParsingConfig, _: ProblemReporter)
private fun parseObjectFromListShorthand(
    psi: YamlValue.Sequence,
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
                    kTrace = psi.asTrace(),
                    pType = listShorthandProperty,
                )
            ),
            type = type, origin = psi,
        )
    }
    // shorthand is unsupported
    reportUnexpectedValue(psi, type)
    return null
}

context(_: Contexts, config: ParsingConfig, _: ProblemReporter)
private fun parsePropertyKeyContexts(
    key: YamlValue,
): Pair<String, Contexts>? {
    val keyText = context(EmptyContexts) {
        parseScalarKey(key, SchemaType.StringType) ?: return null
    }.value as String
    if (config.supportContexts) {
        val keyWithoutContext = keyText.substringBefore('@')
        val context = if (keyWithoutContext === keyText) null else keyText.substringAfter('@')
        if (context != null && '+' in context) {
            reportParsing(key, "multiple.qualifiers.are.unsupported")
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
