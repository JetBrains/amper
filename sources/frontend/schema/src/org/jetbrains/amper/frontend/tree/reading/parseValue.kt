/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.tree.reading

import com.intellij.psi.util.childrenOfType
import org.jetbrains.amper.frontend.api.asTrace
import org.jetbrains.amper.frontend.contexts.Contexts
import org.jetbrains.amper.frontend.contexts.EmptyContexts
import org.jetbrains.amper.frontend.tree.NullValue
import org.jetbrains.amper.frontend.tree.TreeState
import org.jetbrains.amper.frontend.tree.TreeValue
import org.jetbrains.amper.frontend.types.SchemaType
import org.jetbrains.amper.problems.reporting.BuildProblemType
import org.jetbrains.amper.problems.reporting.Level
import org.jetbrains.amper.problems.reporting.ProblemReporter
import org.jetbrains.yaml.psi.YAMLAlias
import org.jetbrains.yaml.psi.YAMLAnchor
import org.jetbrains.yaml.psi.YAMLMapping
import org.jetbrains.yaml.psi.YAMLQuotedText
import org.jetbrains.yaml.psi.YAMLScalar
import org.jetbrains.yaml.psi.YAMLSequence
import org.jetbrains.yaml.psi.YAMLValue

/**
 * Parses the given [psi] value into a [TreeValue] according to the expected [type].
 *
 * If the value in the PSI cannot be parsed into a valid [TreeValue] for the expected [type], an error is reported via
 * the given [ProblemReporter] and null is returned.
 *
 * When there's a new physical value to be parsed, this function should be called, instead of more narrow functions
 * like [parseScalar], because they do not handle the whole context (aliases, type tags, nullability, etc.).
 */
context(inheritedContexts: Contexts, config: ParsingConfig, reporter: ProblemReporter)
internal fun parseValue(
    psi: YAMLValue,
    type: SchemaType,
    explicitContexts: Contexts = EmptyContexts,
): TreeValue<*>? {
    // Unquoted `null` string is treated as the `null` keyword, not a string
    if (psi is YAMLScalar && psi !is YAMLQuotedText && psi.textValue == "null") {
        if (!type.isMarkedNullable) {
            when (type) {
                is SchemaType.EnumType, is SchemaType.PathType, is SchemaType.StringType,
                    -> reportParsing(psi, "validation.types.unexpected.null.stringlike", type = BuildProblemType.TypeMismatch)
                else -> reportParsing(psi, "validation.types.unexpected.null", type = BuildProblemType.TypeMismatch)
            }
            return null // null means invalid in this function, not the null value
        }
        return NullValue<TreeState>(psi.asTrace(), explicitContexts)
    }
    psi.tag?.let { tag ->
        if (tag.text.startsWith("!!")) {
            reportParsing(tag, "validation.structure.unsupported.standard.tag", tag.text)
        } else if (type !is SchemaType.VariantType) {
            reportParsing(tag, "validation.structure.unsupported.tag")
        }
        // tags are allowed for variants sometimes.
    }
    if (psi is YAMLAlias) {
        reportParsing(psi, "validation.structure.unsupported.alias")
        return null
    }

    psi.childrenOfType<YAMLAnchor>().forEach { anchor ->
        reportParsing(anchor, "validation.structure.unsupported.alias")
    }

    // This is required because all "inherited" contexts, that are not explicitly mentioned must not have any traces.
    // Otherwise, some diagnostics report duplicate errors
    // TODO(*): is there a better way around it?
    val newContexts: Contexts = inheritedContexts.map { it.withoutTrace() } + explicitContexts
    context(newContexts) {
        if (config.diagnoseReferences && psi is YAMLScalar) {
            // FIXME: This might be a shorthand/from-key-and-the-rest-nested which contains a reference.
            //  Then we can't really parse it here, so we need this rather on `parseScalar` level.
            val scalar = YAMLScalarOrKey(psi)
            if (containsReferenceSyntax(scalar)) {
                if (config.parseReferences) {
                    return parseReferenceOrInterpolation(scalar, type)
                } else {
                    reportParsing(psi, "validation.types.unsupported.reference", level = Level.Warning)
                }
            }
        }

        return when (type) {
            is SchemaType.ScalarType if psi is YAMLScalar -> parseScalar(YAMLScalarOrKey(psi), type)
            is SchemaType.ListType if psi is YAMLSequence -> parseList(psi, type)
            is SchemaType.MapType if psi is YAMLMapping -> parseMap(psi, type)
            is SchemaType.MapType if psi is YAMLSequence -> parseMapFromSequence(psi, type)
            is SchemaType.ObjectType -> parseObject(psi, type)
            is SchemaType.VariantType -> parseVariant(psi, type)
            else -> {
                reportUnexpectedValue(psi, type)
                null
            }
        }
    }
}
