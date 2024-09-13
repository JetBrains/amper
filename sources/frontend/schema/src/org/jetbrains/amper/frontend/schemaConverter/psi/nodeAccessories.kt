/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.schemaConverter.psi

import com.intellij.psi.PsiElement
import org.jetbrains.amper.core.messages.ProblemReporterContext
import org.jetbrains.amper.frontend.api.PsiTrace
import org.jetbrains.amper.frontend.api.Traceable
import org.jetbrains.yaml.psi.YAMLKeyValue

/**
 * Unwraps a map entry, providing its value if possible. Valid only for YAML
 */
val PsiElement.unwrapKey get() = (this as? YAMLKeyValue)?.value ?: this

/**
 * Try to cast the current node to a scalar.
 */
fun PsiElement.asScalarNode() = Scalar.from(unwrapKey)

context(ProblemReporterContext)
fun PsiElement.asSequenceNode() = Sequence.from(unwrapKey)

/**
 * Map the contents of a sequence as a list of scalars.
 */
fun Sequence.asScalarSequenceNode() : List<Scalar> = items.mapNotNull { it.asScalarNode() }

context(ProblemReporterContext)
fun PsiElement.asMappingNode() = MappingNode.from(this)

context(ProblemReporterContext)
fun MappingEntry.asMappingNode() = sourceElement.asMappingNode()

fun <T : Traceable> T.applyPsiTrace(element: MappingEntry?) = apply { trace = element?.sourceElement?.let(::PsiTrace) }