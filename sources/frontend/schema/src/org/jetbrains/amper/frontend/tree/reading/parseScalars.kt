/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.tree.reading

import org.jetbrains.amper.frontend.contexts.Contexts
import org.jetbrains.amper.frontend.tree.ScalarNode
import org.jetbrains.amper.frontend.tree.reading.maven.validateAndReportMavenCoordinates
import org.jetbrains.amper.frontend.types.SchemaType
import org.jetbrains.amper.frontend.types.render
import org.jetbrains.amper.problems.reporting.BuildProblemType
import org.jetbrains.amper.problems.reporting.ProblemReporter
import java.nio.file.InvalidPathException
import kotlin.io.path.Path

context(_: Contexts, _: ParsingConfig, _: ProblemReporter)
internal fun parseScalar(scalar: YamlValue.Scalar, type: SchemaType.ScalarType): ScalarNode? = when (type) {
    is SchemaType.BooleanType -> when(val boolean = scalar.textValue.toBooleanStrictOrNull()) {
        null -> {
            reportParsing(scalar, "validation.expected", type.render(), type = BuildProblemType.TypeMismatch)
            null
        }
        else -> booleanNode(scalar, type, boolean)
    }
    is SchemaType.IntType -> when(val int = scalar.textValue.toIntOrNull()) {
        null -> {
            reportParsing(scalar, "validation.expected", type.render(), type = BuildProblemType.TypeMismatch)
            null
        }
        else -> intNode(scalar, type, int)
    }
    is SchemaType.StringType -> {
        stringNode(scalar, type, scalar.textValue).takeIf {
            when (type.semantics) {
                SchemaType.StringType.Semantics.JvmMainClass,
                SchemaType.StringType.Semantics.PluginSettingsClass,
                null -> true
                SchemaType.StringType.Semantics.MavenCoordinates -> validateAndReportMavenCoordinates(
                    origin = scalar.psi,
                    coordinates = scalar.textValue,
                )
            }
        }
    }
    is SchemaType.EnumType -> parseEnum(scalar, type)
    is SchemaType.PathType -> parsePath(scalar, type)
}


context(_: Contexts, config: ParsingConfig, _: ProblemReporter)
private fun parsePath(scalar: YamlValue.Scalar, type: SchemaType.PathType): ScalarNode? {
    var path = try {
        Path(scalar.textValue)
    } catch (e: InvalidPathException) {
        reportParsing(scalar, "validation.types.invalid.path", e.message)
        return null
    }
    path = if (path.isAbsolute) path else config.basePath.resolve(path)
    path = path.normalize()
    return pathNode(scalar, type, path)
}

context(_: Contexts, _: ProblemReporter)
internal fun parseEnum(
    scalar: YamlValue.Scalar,
    type: SchemaType.EnumType,
    additionalSuggestedValues: List<String> = emptyList(),
): ScalarNode? {
    val textValue = scalar.textValue
    val entry = type.declaration.getEntryBySchemaValue(textValue)
    if (entry == null) {
        val suggestedValues = additionalSuggestedValues + type.declaration.entries
            .filter { it.isIncludedIntoJsonSchema && !it.isOutdated }
            .map { it.schemaValue }
        reportParsing(
            scalar, "validation.types.unknown.enum.value",
            textValue, suggestedValues.joinToString(),
            type = BuildProblemType.TypeMismatch,
        )
        return null
    }
    return enumNode(scalar, type, entry.name)
}
