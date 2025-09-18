/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.tree.reading

import com.intellij.psi.PsiElement
import com.intellij.psi.util.elementType
import org.jetbrains.amper.problems.reporting.Level
import org.jetbrains.amper.problems.reporting.ProblemReporter
import org.jetbrains.yaml.YAMLTokenTypes
import org.jetbrains.yaml.psi.YAMLKeyValue
import org.jetbrains.yaml.psi.YAMLScalar

internal class YAMLScalarOrKey private constructor(
    val psi: PsiElement,
    val textValue: String,
) {
    constructor(scalar: YAMLScalar) : this(psi = scalar, textValue = scalar.textValue)

    companion object {

        context(reporter: ProblemReporter, config: ParsingConfig)
        fun parseKey(keyValue: YAMLKeyValue): YAMLScalarOrKey? {
            val key = keyValue.key ?: run {
                reportParsing(keyValue, "validation.structure.missing.key")
                return null
            }
            val tag = keyValue.allChildren().find { it.elementType == YAMLTokenTypes.TAG }
            if (tag != null) {
                if (tag.text.startsWith("!!")) {
                    reportParsing(tag, "validation.structure.unsupported.standard.tag", tag.text)
                } else {
                    reportParsing(tag, "validation.structure.unsupported.tag")
                }
            }
            if (key !is YAMLScalar && key.elementType != YAMLTokenTypes.SCALAR_KEY) {
                reportParsing(key, "validation.types.unexpected.compound.key")
                return null
            }
            val scalarKey = YAMLScalarOrKey(key, textValue = keyValue.keyText)
            if (config.diagnoseReferences && containsReferenceSyntax(scalarKey)) {
                reportParsing(key, "validation.types.unsupported.reference.key", "key", level = Level.Warning)
            }
            return scalarKey
        }

        operator fun invoke(psi: PsiElement): YAMLScalarOrKey? =
            if (psi is YAMLScalar || psi.elementType == YAMLTokenTypes.SCALAR_KEY) YAMLScalarOrKey(psi = psi) else null
    }
}