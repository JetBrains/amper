/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.schemaConverter.psi.yaml

import org.jetbrains.amper.core.messages.ProblemReporterContext
import org.jetbrains.yaml.psi.YAMLKeyValue
import org.jetbrains.yaml.psi.YAMLMapping
import org.jetbrains.yaml.psi.YAMLPsiElement
import org.jetbrains.yaml.psi.YAMLScalar
import org.jetbrains.yaml.psi.YAMLSequence


/**
 * Unwraps [YAMLKeyValue], providing its value if possible.
 */
val YAMLPsiElement.unwrapKey get() = (this as? YAMLKeyValue)?.value ?: this

/**
 * Try to cast current node to [YAMLScalar].
 * Report if node has different type.
 */
fun YAMLPsiElement.asScalarNode() = unwrapKey as? YAMLScalar

/**
 * Try to cast current node to [YAMLSequence].
 * Report if node has different type.
 */
context(ProblemReporterContext)
fun YAMLPsiElement.asSequenceNode() = unwrapKey as? YAMLSequence

/**
 * Try to cast current node to [YAMLSequence] and map its contents as list of [YAMLScalar].
 * Report if node has different type.
 */
context(ProblemReporterContext)
fun YAMLPsiElement.asScalarSequenceNode() : List<YAMLScalar>? = (unwrapKey as? YAMLSequence)
    ?.items
    ?.mapNotNull { it.value as? YAMLScalar }

/**
 * Try to cast current node to [MappingNode].
 * Report if node has different type.
 */
context(ProblemReporterContext)
fun YAMLPsiElement.asMappingNode() = unwrapKey as? YAMLMapping

/**
 * Try to find child node by given name.
 * Report if no node found.
 */
context(ProblemReporterContext)
fun YAMLMapping.tryGetChildNode(name: String): YAMLKeyValue? =
    keyValues.firstOrNull { it.keyText == name }
