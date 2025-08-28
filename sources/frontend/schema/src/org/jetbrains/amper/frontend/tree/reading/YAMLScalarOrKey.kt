/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.tree.reading

import com.intellij.psi.PsiElement
import org.jetbrains.amper.problems.reporting.Level
import org.jetbrains.amper.problems.reporting.ProblemReporter
import org.jetbrains.yaml.YAMLTokenTypes
import org.jetbrains.yaml.psi.YAMLKeyValue
import org.jetbrains.yaml.psi.YAMLScalar
import org.toml.lang.psi.ext.elementType

@JvmInline
internal value class YAMLScalarOrKey private constructor(val psi: PsiElement) {
    constructor(scalar: YAMLScalar) : this(psi = scalar)

    val textValue: String get() = when (psi) {
        is YAMLScalar -> psi.textValue
        else -> psi.text  // For YAMLKeyValue.key, which is not a YAMLValue for some reason.
    }

    companion object {

        context(reporter: ProblemReporter)
        fun parseKey(keyValue: YAMLKeyValue): YAMLScalarOrKey? {
            val key = keyValue.key ?: run {
                reportParsing(keyValue, "validation.structure.missing.key")
                return null
            }
            val tag = keyValue.children.find { it.elementType == YAMLTokenTypes.TAG }
            if (tag != null) {
                reportParsing(tag, "validation.structure.unsupported.tag")
                return null
            }
            if (key !is YAMLScalar && key.elementType != YAMLTokenTypes.SCALAR_KEY) {
                reportParsing(key, "validation.types.unexpected.compound.key")
                return null
            }
            val scalarKey = YAMLScalarOrKey(key)
            if (containsReferenceSyntax(scalarKey)) {
                reportParsing(key, "validation.types.unsupported.reference", level = Level.Warning)
            }
            return scalarKey
        }

        operator fun invoke(psi: PsiElement): YAMLScalarOrKey? =
            if (psi is YAMLScalar || psi.elementType == YAMLTokenTypes.SCALAR_KEY) YAMLScalarOrKey(psi = psi) else null
    }
}