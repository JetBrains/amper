/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.schemaConverter.psi.amper

import com.intellij.amper.lang.AmperElement
import com.intellij.amper.lang.AmperObject
import com.intellij.amper.lang.AmperProperty
import org.jetbrains.amper.core.messages.ProblemReporterContext


/**
 * Unwraps [YAMLKeyValue], providing its value if possible.
 */
val AmperElement.unwrapKey get() = (this as? AmperProperty)?.value ?: this

/**
 * Try to cast current node to [YAMLSequence].
 * Report if node has different type.
 */
context(ProblemReporterContext)
fun AmperElement.asSequenceNode() = unwrapKey as? AmperObject

/**
 * Try to cast current node to [MappingNode].
 * Report if node has different type.
 */
context(ProblemReporterContext)
fun AmperElement.asMappingNode() = unwrapKey as? AmperObject

