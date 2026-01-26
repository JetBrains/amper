/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.tree.reading

import com.intellij.psi.util.childrenOfType
import org.jetbrains.amper.frontend.contexts.Contexts
import org.jetbrains.amper.frontend.contexts.EmptyContexts
import org.jetbrains.amper.frontend.tree.NullLiteralNode
import org.jetbrains.amper.frontend.tree.TreeNode
import org.jetbrains.amper.frontend.types.SchemaType
import org.jetbrains.amper.problems.reporting.BuildProblemType
import org.jetbrains.amper.problems.reporting.Level
import org.jetbrains.amper.problems.reporting.ProblemReporter
import org.jetbrains.yaml.psi.YAMLAnchor

/**
 * Parses the given [value] value into a [TreeNode] according to the expected [type].
 *
 * If the value in the PSI cannot be parsed into a valid [TreeNode] for the expected [type], an error is reported via
 * the given [ProblemReporter] and null is returned.
 *
 * When there's a new physical value to be parsed, this function should be called, instead of more narrow functions
 * like [parseScalar], because they do not handle the whole context (aliases, type tags, nullability, etc.).
 */
context(inheritedContexts: Contexts, config: ParsingConfig, reporter: ProblemReporter)
internal fun parseNode(
    value: YamlValue,
    type: SchemaType,
    explicitContexts: Contexts = EmptyContexts,
): TreeNode? {
    // Unquoted `null` string is treated as the `null` keyword, not a string
    if (value is YamlValue.Scalar && value.isLiteral && value.textValue == "null") {
        if (!type.isMarkedNullable) {
            when (type) {
                is SchemaType.EnumType, is SchemaType.PathType, is SchemaType.StringType,
                    -> reportParsing(value, "validation.types.unexpected.null.stringlike", type = BuildProblemType.TypeMismatch)
                else -> reportParsing(value, "validation.types.unexpected.null", type = BuildProblemType.TypeMismatch)
            }
            return null // null means invalid in this function, not the null value
        }
        return NullLiteralNode(value.asTrace(), explicitContexts)
    }
    value.tag?.let { tag ->
        if (tag.text.startsWith("!!")) {
            reportParsing(tag, "validation.structure.unsupported.standard.tag", tag.text)
        } else if (type !is SchemaType.VariantType && type !is SchemaType.ObjectType) {
            reportParsing(tag, "validation.structure.unsupported.tag")
        }
        // tags are allowed for variants sometimes.
    }

    value.psi.childrenOfType<YAMLAnchor>().forEach { anchor ->
        reportParsing(anchor, "validation.structure.unsupported.alias")
    }

    // This is required because all "inherited" contexts, that are not explicitly mentioned must not have any traces.
    // Otherwise, some diagnostics report duplicate errors
    // TODO(*): is there a better way around it?
    val newContexts: Contexts = inheritedContexts.map { it.withoutTrace() } + explicitContexts
    context(newContexts) {
        if (config.diagnoseReferences && value is YamlValue.Scalar) {
            // FIXME: This might be a shorthand/from-key-and-the-rest-nested which contains a reference.
            //  Then we can't really parse it here, so we need this rather on `parseScalar` level.
            if (containsReferenceSyntax(value)) {
                if (config.parseReferences) {
                    return parseReferenceOrInterpolation(value, type)
                } else {
                    reportParsing(value, "validation.types.unsupported.reference", level = Level.Warning)
                }
            }
        }

        return when (type) {
            is SchemaType.ScalarType if value is YamlValue.Scalar -> parseScalar(value, type)
            is SchemaType.ListType if value is YamlValue.Sequence -> parseList(value, type)
            is SchemaType.MapType if value is YamlValue.Mapping -> parseMap(value, type)
            is SchemaType.MapType if value is YamlValue.Sequence -> parseMapFromSequence(value, type)
            is SchemaType.ObjectType -> parseObject(value, type)
            is SchemaType.VariantType -> parseVariant(value, type)
            else -> {
                reportUnexpectedValue(value, type)
                null
            }
        }
    }
}
