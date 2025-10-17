/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.tree.reading

import com.intellij.psi.PsiElement
import com.intellij.psi.util.elementType
import org.jetbrains.amper.frontend.api.Trace
import org.jetbrains.amper.frontend.api.asTrace
import org.jetbrains.amper.frontend.asBuildProblemSource
import org.jetbrains.amper.frontend.contexts.Contexts
import org.jetbrains.amper.frontend.reportBundleError
import org.jetbrains.amper.frontend.tree.ErrorValue
import org.jetbrains.amper.frontend.tree.ListValue
import org.jetbrains.amper.frontend.tree.MapLikeChildren
import org.jetbrains.amper.frontend.tree.MapLikeValue
import org.jetbrains.amper.frontend.tree.NullValue
import org.jetbrains.amper.frontend.tree.Owned
import org.jetbrains.amper.frontend.tree.ReferenceValue
import org.jetbrains.amper.frontend.tree.ScalarValue
import org.jetbrains.amper.frontend.tree.StringInterpolationValue
import org.jetbrains.amper.frontend.tree.TreeState
import org.jetbrains.amper.frontend.tree.TreeValue
import org.jetbrains.amper.frontend.tree.copy
import org.jetbrains.amper.frontend.types.SchemaType
import org.jetbrains.amper.frontend.types.render
import org.jetbrains.amper.problems.reporting.BuildProblemType
import org.jetbrains.amper.problems.reporting.Level
import org.jetbrains.amper.problems.reporting.ProblemReporter
import org.jetbrains.yaml.YAMLTokenTypes
import org.jetbrains.yaml.psi.YAMLCompoundValue
import org.jetbrains.yaml.psi.YAMLMapping
import org.jetbrains.yaml.psi.YAMLScalar
import org.jetbrains.yaml.psi.YAMLSequence

context(contexts: Contexts)
internal fun scalarValue(origin: YAMLScalarOrKey, type: SchemaType.ScalarType, value: Any) =
    ScalarValue<TreeState>(value, type, origin.psi.asTrace(), contexts)

context(contexts: Contexts)
internal fun mapLikeValue(
    origin: PsiElement,
    type: SchemaType.MapLikeType,
    children: MapLikeChildren<TreeState>,
) = Owned(
    children = children,
    type = type,
    trace = origin.asTrace(),
    contexts = contexts,
)

internal fun formatValueForMessage(psi: PsiElement) = when (psi) {
    is YAMLScalar -> "scalar"
    is YAMLSequence -> "sequence []"
    is YAMLMapping -> "mapping {}"
    is YAMLCompoundValue -> "compound value {}"
    else -> when (psi.elementType) {
        YAMLTokenTypes.SCALAR_KEY -> "scalar"
        else -> error("Unexpected PsiElement $psi")
    }
}

context(reporter: ProblemReporter)
internal fun reportUnexpectedValue(unexpected: PsiElement, expectedType: SchemaType) {
    reportParsing(
        unexpected, "validation.types.unexpected.value",
        expectedType.render(), formatValueForMessage(unexpected),
        type = BuildProblemType.TypeMismatch,
    )
}

context(reporter: ProblemReporter)
internal fun reportParsing(
    psi: PsiElement,
    messageKey: String,
    vararg args: Any?,
    level: Level = Level.Error,
    type: BuildProblemType = BuildProblemType.Generic,
) {
    reporter.reportBundleError(psi.asBuildProblemSource(), messageKey, *args, level = level, problemType = type)
}

// guarantees to include non-compound child elements
internal fun PsiElement.allChildren(): Sequence<PsiElement> = sequence {
    var current: PsiElement? = firstChild
    while (current != null) {
        yield(current)
        current = current.nextSibling
    }
}

internal fun <T : TreeState> TreeValue<T>.copyWithTrace(trace: Trace): TreeValue<T> {
    return when (this) {
        is ListValue<T> -> copy(trace = trace)
        is MapLikeValue<T> -> copy(trace = trace)
        is ErrorValue -> ErrorValue(trace = trace)
        is ReferenceValue<T> -> copy(trace = trace)
        is StringInterpolationValue<T> -> copy(trace = trace)
        is ScalarValue<T> -> copy(trace = trace)
        is NullValue<T> -> copy(trace = trace)
    }
}

internal val ParsingConfig.parseReferences: Boolean get() = when (referenceParsingMode) {
    ReferencesParsingMode.Parse -> true
    ReferencesParsingMode.Ignore, ReferencesParsingMode.IgnoreButWarn -> false
}


internal val ParsingConfig.diagnoseReferences: Boolean get() = when (referenceParsingMode) {
    ReferencesParsingMode.Parse, ReferencesParsingMode.IgnoreButWarn -> true
    ReferencesParsingMode.Ignore -> false
}
