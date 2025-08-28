/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.tree.reading

import org.jetbrains.amper.frontend.api.asTrace
import org.jetbrains.amper.frontend.contexts.Contexts
import org.jetbrains.amper.frontend.tree.ReferenceValue
import org.jetbrains.amper.frontend.tree.TreeState
import org.jetbrains.amper.frontend.types.SchemaType
import org.jetbrains.amper.frontend.types.render
import org.jetbrains.amper.problems.reporting.ProblemReporter
import org.jetbrains.amper.stdlib.regex.getValue

context(contexts: Contexts, _: ParsingConfig, _: ProblemReporter)
internal fun parseReference(
    scalar: YAMLScalarOrKey,
    type: SchemaType,
): ReferenceValue<*>? {
    val textValue = scalar.textValue
    val match = checkNotNull(ReferenceSyntax.matchEntire(textValue)) {
        "Should not be called unless 'containsReferenceSyntax' is true"
    }
    val prefix by match
    val suffix by match
    val reference by match
    val closingBrace by match
    if (closingBrace == null) {
        // TODO: more granular range reporting
        reportParsing(scalar.psi, "validation.reference.missing.closing.brace")
        // Return some reference
        return null
    }

    val hasInterpolation = !prefix.isNullOrEmpty() || !suffix.isNullOrEmpty()
    if (hasInterpolation && !type.supportsInterpolation()) {
        // TODO: more granular range reporting
        reportParsing(scalar.psi, "validation.types.unsupported.interpolation", type.render())
        return null
    }

    return ReferenceValue<TreeState>(
        value = reference!!,
        trace = scalar.psi.asTrace(),
        contexts = contexts,
        prefix = prefix.orEmpty(),
        suffix = suffix.orEmpty(),
        type = type,
    )
}

internal fun containsReferenceSyntax(scalar: YAMLScalarOrKey): Boolean {
    val textValue = scalar.textValue
    return ReferenceSyntax.matchEntire(textValue) != null
}

private fun SchemaType.supportsInterpolation() = when(this) {
    is SchemaType.PathType, is SchemaType.StringType -> true
    else -> false
}

// The closing brace is optional here, so when this matches, it doesn't mean that the reference is valid.
private val ReferenceSyntax =
    """^(?<prefix>.*?)\$\{(?<reference>[^{}\n]*)(?<closingBrace>})?(?<suffix>.*)$""".toRegex()
