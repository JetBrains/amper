/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.tree.reading

import org.jetbrains.amper.frontend.api.asTrace
import org.jetbrains.amper.frontend.api.asTraceable
import org.jetbrains.amper.frontend.contexts.Contexts
import org.jetbrains.amper.frontend.tree.ScalarValue
import org.jetbrains.amper.frontend.types.SchemaType
import org.jetbrains.amper.problems.reporting.BuildProblemType
import org.jetbrains.amper.problems.reporting.ProblemReporter
import java.nio.file.InvalidPathException
import kotlin.io.path.Path

context(_: Contexts, _: ParsingConfig, _: ProblemReporter)
internal fun parseScalar(scalar: YAMLScalarOrKey, type: SchemaType.ScalarType): ScalarValue<*>? = when (type) {
    is SchemaType.BooleanType -> when(val boolean = scalar.textValue.toBooleanStrictOrNull()) {
        null -> {
            reportParsing(scalar.psi, "validation.expected.boolean", type = BuildProblemType.TypeMismatch)
            null
        }
        else -> scalarValue(scalar, boolean)
    }
    is SchemaType.IntType -> when(val int = scalar.textValue.toIntOrNull()) {
        null -> {
            reportParsing(scalar.psi, "validation.expected.integer", type = BuildProblemType.TypeMismatch)
            null
        }
        else -> scalarValue(scalar, int)
    }
    is SchemaType.StringType -> {
        val string = scalar.textValue
        val value = if (type.isTraceableWrapped) string.asTraceable(scalar.psi.asTrace()) else string
        scalarValue(scalar, value)
    }
    is SchemaType.EnumType -> parseEnum(scalar, type)
    is SchemaType.PathType -> parsePath(scalar, type)
}


context(_: Contexts, config: ParsingConfig, _: ProblemReporter)
private fun parsePath(scalar: YAMLScalarOrKey, type: SchemaType.PathType): ScalarValue<*>? {
    var path = try {
        Path(scalar.textValue)
    } catch (e: InvalidPathException) {
        reportParsing(scalar.psi, "validation.types.invalid.path", e.message)
        return null
    }
    path = if (path.isAbsolute) path else config.basePath.resolve(path)
    path = path.normalize()
    val value = if (type.isTraceableWrapped) path.asTraceable(scalar.psi.asTrace()) else path
    return scalarValue(scalar, value)
}

context(_: Contexts, _: ProblemReporter)
private fun parseEnum(scalar: YAMLScalarOrKey, type: SchemaType.EnumType): ScalarValue<*>? {
    val textValue = scalar.textValue
    val entry = type.declaration.getEntryBySchemaValue(textValue)
    if (entry == null) {
        val suggestedValues =  type.declaration.entries
            .filter { it.isIncludedIntoJsonSchema && !it.isOutdated }.joinToString { it.schemaValue }
        reportParsing(
            scalar.psi, "validation.types.unknown.enum.value",
            textValue, suggestedValues, type = BuildProblemType.TypeMismatch,
        )
        return null
    }

    // Can be Enum<*> for builtin enums, or String for user-defined enums.
    val enumConstant = type.declaration.toEnumConstant(entry.name)

    val value = if (type.isTraceableWrapped) {
        check(enumConstant is Enum<*>) { "Not reached: only builtin enums can be wrapped into a TraceableValue" }
        enumConstant.asTraceable(scalar.psi.asTrace())
    } else enumConstant

    return scalarValue(scalar, value)
}
