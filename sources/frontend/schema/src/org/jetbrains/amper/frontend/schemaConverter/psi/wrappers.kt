/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.schemaConverter.psi

import com.intellij.amper.lang.AmperLiteral
import com.intellij.amper.lang.AmperProperty
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiElement
import org.jetbrains.yaml.psi.YAMLKeyValue
import org.jetbrains.yaml.psi.YAMLScalar

interface AmperElementWrapper {
    val sourceElement: PsiElement
}

class Scalar(override val sourceElement: PsiElement): AmperElementWrapper {
    companion object {
        fun from(element: PsiElement?) =
            when (element) {
                is YAMLScalar, is AmperLiteral -> Scalar(element)
                else -> null
            }
    }

    val textValue get() =
        when (sourceElement) {
            is YAMLScalar -> sourceElement.textValue
            else -> StringUtil.unquoteString(sourceElement.text)
        }
}

class UnknownElementWrapper(override val sourceElement: PsiElement): AmperElementWrapper

class MappingEntry(override val sourceElement: PsiElement): AmperElementWrapper {

    companion object {
        fun from(element: PsiElement?): MappingEntry? {
            return when (element) {
                is YAMLKeyValue, is AmperProperty -> MappingEntry(element)
                else -> null
            }
        }

        fun byValue(valueElement: PsiElement): MappingEntry? {
            val parent = valueElement.parent
            if (parent is YAMLKeyValue && parent.value == valueElement) {
                return MappingEntry(parent)
            }
            if (parent is AmperProperty && parent.value == valueElement) {
                return MappingEntry(parent)
            }
            return null
        }

        fun byKey(valueElement: PsiElement): MappingEntry? {
            val parent = valueElement.parent
            if (parent is YAMLKeyValue && parent.key == valueElement) {
                return MappingEntry(parent)
            }
            if (parent is AmperProperty && parent.nameElement == valueElement) {
                return MappingEntry(parent)
            }
            return null
        }
    }

    val value get() = when (sourceElement) {
        is YAMLKeyValue -> sourceElement.value
        is AmperProperty -> sourceElement.value
        else -> null
    }
}