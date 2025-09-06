/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.tree.reading

import com.intellij.psi.PsiElement
import org.jetbrains.amper.frontend.api.Trace
import org.jetbrains.amper.frontend.api.asTrace
import org.jetbrains.amper.frontend.asBuildProblemSource
import org.jetbrains.amper.frontend.contexts.Contexts
import org.jetbrains.amper.frontend.reportBundleError
import org.jetbrains.amper.frontend.tree.ListValue
import org.jetbrains.amper.frontend.tree.MapLikeChildren
import org.jetbrains.amper.frontend.tree.MapLikeValue
import org.jetbrains.amper.frontend.tree.NoValue
import org.jetbrains.amper.frontend.tree.NullValue
import org.jetbrains.amper.frontend.tree.Owned
import org.jetbrains.amper.frontend.tree.ReferenceValue
import org.jetbrains.amper.frontend.tree.ScalarValue
import org.jetbrains.amper.frontend.tree.TreeState
import org.jetbrains.amper.frontend.tree.TreeValue
import org.jetbrains.amper.frontend.tree.copy
import org.jetbrains.amper.frontend.types.SchemaType
import org.jetbrains.amper.problems.reporting.BuildProblemType
import org.jetbrains.amper.problems.reporting.Level
import org.jetbrains.amper.problems.reporting.ProblemReporter
import org.jetbrains.yaml.psi.YAMLCompoundValue
import org.jetbrains.yaml.psi.YAMLMapping
import org.jetbrains.yaml.psi.YAMLScalar
import org.jetbrains.yaml.psi.YAMLSequence
import org.jetbrains.yaml.psi.YAMLValue

context(contexts: Contexts)
internal fun scalarValue(origin: YAMLScalarOrKey, value: Any) =
    ScalarValue<TreeState>(value, origin.psi.asTrace(), contexts)

context(contexts: Contexts)
internal fun mapLikeValue(
    origin: PsiElement,
    type: SchemaType.ObjectType?,
    children: MapLikeChildren<TreeState>,
) = Owned(
    children = children,
    type = type?.declaration,
    trace = origin.asTrace(),
    contexts = contexts,
)

internal fun formatValueForMessage(scalar: YAMLScalarOrKey) = "scalar ''${scalar.textValue}''"

internal fun formatValueForMessage(psi: YAMLValue) = when(psi) {
    is YAMLScalar -> "scalar ''${psi.textValue}''"
    is YAMLSequence -> "sequence [...]"
    is YAMLMapping -> "mapping {...}"
    is YAMLCompoundValue -> psi.textValue // TODO: not clear what is compound value that is not a list or a map
    else -> "''${psi.text}''"
}

context(reporter: ProblemReporter)
internal fun reportTypeTag(value: YAMLValue) {
    value.tag?.let {
        reporter.reportBundleError(it.asBuildProblemSource(), "validation.structure.unsupported.tag")
    }
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

internal fun <T : TreeState> TreeValue<T>.copyWithTrace(trace: Trace): TreeValue<T> {
    return when(this) {
        is ListValue<T> -> copy(trace = trace)
        is MapLikeValue<T> -> copy(trace = trace)
        is NoValue -> NoValue(trace = trace)
        is ReferenceValue<T> -> copy(trace = trace)
        is ScalarValue<T> -> copy(trace = trace)
        is NullValue<T> -> copy(trace = trace)
    }
}