/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.tree.reading

import com.intellij.psi.PsiElement
import org.jetbrains.amper.frontend.api.asTrace
import org.jetbrains.amper.frontend.asBuildProblemSource
import org.jetbrains.amper.frontend.contexts.Contexts
import org.jetbrains.amper.frontend.reportBundleError
import org.jetbrains.amper.frontend.tree.BooleanNode
import org.jetbrains.amper.frontend.tree.EnumNode
import org.jetbrains.amper.frontend.tree.IntNode
import org.jetbrains.amper.frontend.tree.KeyValue
import org.jetbrains.amper.frontend.tree.MappingNode
import org.jetbrains.amper.frontend.tree.PathNode
import org.jetbrains.amper.frontend.tree.StringNode
import org.jetbrains.amper.frontend.types.SchemaType
import org.jetbrains.amper.frontend.types.render
import org.jetbrains.amper.problems.reporting.BuildProblemType
import org.jetbrains.amper.problems.reporting.Level
import org.jetbrains.amper.problems.reporting.ProblemReporter
import java.nio.file.Path

context(contexts: Contexts)
internal fun booleanNode(origin: YamlValue.Scalar, type: SchemaType.BooleanType, value: Boolean) =
    BooleanNode(value, type, origin.asTrace(), contexts)

context(contexts: Contexts)
internal fun stringNode(origin: YamlValue.Scalar, type: SchemaType.StringType, value: String) =
    StringNode(value, type, origin.asTrace(), contexts)

context(contexts: Contexts)
internal fun intNode(origin: YamlValue.Scalar, type: SchemaType.IntType, value: Int) =
    IntNode(value, type, origin.asTrace(), contexts)

context(contexts: Contexts)
internal fun enumNode(origin: YamlValue.Scalar, type: SchemaType.EnumType, value: String) =
    EnumNode(value, type, origin.asTrace(), contexts)

context(contexts: Contexts)
internal fun pathNode(origin: YamlValue.Scalar, type: SchemaType.PathType, value: Path) =
    PathNode(value, type, origin.asTrace(), contexts)

context(contexts: Contexts)
internal fun mappingNode(
    origin: YamlValue,
    type: SchemaType.MapLikeType,
    children: List<KeyValue>,
) = MappingNode(
    children = children,
    type = type,
    trace = origin.asTrace(),
    contexts = contexts,
)

context(reporter: ProblemReporter)
internal fun reportUnexpectedValue(
    unexpected: YamlValue,
    expectedType: SchemaType,
    renderOnlyNestedTypeSyntax: Boolean = true,
) {
    val valueForMessage = when (unexpected) {
        is YamlValue.Mapping -> "mapping {}"
        is YamlValue.Scalar -> "scalar"
        is YamlValue.Sequence -> "sequence []"
        is YamlValue.UnknownCompound -> "compound value {}"
        is YamlValue.Missing -> {
            reportParsing(unexpected, "validation.structure.missing.value")
            return
        }
        is YamlValue.Alias -> {
            reportParsing(unexpected, "validation.structure.unsupported.alias")
            return
        }
    }
    val typeString = expectedType.render(
        onlyNested = renderOnlyNestedTypeSyntax,
    )
    reportParsing(
        unexpected, "validation.types.unexpected.value",
        typeString, valueForMessage,
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

context(reporter: ProblemReporter)
internal fun reportParsing(
    value: YamlValue,
    messageKey: String,
    vararg args: Any?,
    level: Level = Level.Error,
    type: BuildProblemType = BuildProblemType.Generic,
) {
    reporter.reportBundleError(value.psi.asBuildProblemSource(), messageKey, *args, level = level, problemType = type)
}

// guarantees to include non-compound child elements
internal fun PsiElement.allChildren(): Sequence<PsiElement> = sequence {
    var current: PsiElement? = firstChild
    while (current != null) {
        yield(current)
        current = current.nextSibling
    }
}

internal fun YamlValue.asTrace() = psi.asTrace()

internal fun YamlKeyValue.asTrace() = psi.asTrace()

internal val ParsingConfig.parseReferences: Boolean get() = when (referenceParsingMode) {
    ReferencesParsingMode.Parse -> true
    ReferencesParsingMode.Ignore, ReferencesParsingMode.IgnoreButWarn -> false
}

internal val ParsingConfig.diagnoseReferences: Boolean get() = when (referenceParsingMode) {
    ReferencesParsingMode.Parse, ReferencesParsingMode.IgnoreButWarn -> true
    ReferencesParsingMode.Ignore -> false
}
