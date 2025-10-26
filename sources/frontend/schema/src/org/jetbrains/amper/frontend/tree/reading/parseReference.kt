/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.tree.reading

import org.jetbrains.amper.frontend.contexts.Contexts
import org.jetbrains.amper.frontend.tree.ReferenceValue
import org.jetbrains.amper.frontend.tree.StringInterpolationValue
import org.jetbrains.amper.frontend.tree.TreeState
import org.jetbrains.amper.frontend.tree.TreeValue
import org.jetbrains.amper.frontend.types.SchemaType
import org.jetbrains.amper.frontend.types.render
import org.jetbrains.amper.problems.reporting.ProblemReporter
import org.jetbrains.amper.stdlib.regex.getValue

context(contexts: Contexts, _: ParsingConfig, _: ProblemReporter)
internal fun parseReferenceOrInterpolation(
    scalar: YamlValue.Scalar,
    type: SchemaType,
): TreeValue<*>? {
    val parts = mutableListOf<StringInterpolationValue.Part>()
    splitIntoParts(
        scalar.textValue,
        onMatch = { match ->
            // TODO: more granular range reporting
            val reference by match
            val closingBrace by match
            if (closingBrace == null) {
                reportParsing(scalar, "validation.reference.missing.closing.brace")
                return null
            }
            val referenceText = reference
            if (referenceText.isNullOrBlank()) {
                reportParsing(scalar, "validation.reference.empty")
                return null
            }
            if ('$' in referenceText || '{' in referenceText) {
                reportParsing(scalar, "validation.reference.nested")
                return null
            }
            val referencePath = referenceText.split('.')
            if (referencePath.any { it.isEmpty() }) {
                reportParsing(scalar, "validation.reference.empty.element")
            }
            parts.add(StringInterpolationValue.Part.Reference(referencePath))
        },
        onText = { text ->
            parts.add(StringInterpolationValue.Part.Text(text))
        },
    )
    require(parts.isNotEmpty())

    return if (parts.size > 1) {
        if (type !is SchemaType.StringInterpolatableType) {
            // TODO: more granular range reporting
            reportParsing(scalar, "validation.types.unsupported.interpolation", type.render(includeSyntax = false))
            return null
        }
        StringInterpolationValue<TreeState>(
            parts = parts,
            trace = scalar.asTrace(),
            contexts = contexts,
            type = type,
        )
    } else {
        val reference = checkNotNull(parts.first() as StringInterpolationValue.Part.Reference) {
            "Should not be called unless 'containsReferenceSyntax' is true"
        }
        ReferenceValue<TreeState>(
            referencedPath = reference.referencePath,
            trace = scalar.asTrace(),
            contexts = contexts,
            type = type,
        )
    }
}

private inline fun splitIntoParts(
    input: String,
    onMatch: (MatchResult) -> Unit,
    onText: (String) -> Unit,
) {
    var position = 0
    while (position < input.length) {
        val match = ReferenceSyntax.find(input, position)
        if (match != null) {
            if (match.range.first > position) {
                onText(input.substring(position, match.range.first))
            }
            onMatch(match)
            position = match.range.last + 1
        } else {
            onText(input.substring(position))
            break
        }
    }
}

internal fun containsReferenceSyntax(scalar: YamlValue.Scalar): Boolean {
    return ReferenceSyntax.containsMatchIn(scalar.textValue)
}

// The closing brace is optional here, so when this matches, it doesn't mean that the reference is valid.
private val ReferenceSyntax =
    """\$\{(?<reference>[^}\n]*)(?<closingBrace>})?""".toRegex()
