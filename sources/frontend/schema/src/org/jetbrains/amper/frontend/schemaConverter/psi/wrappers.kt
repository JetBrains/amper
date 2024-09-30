/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.schemaConverter.psi

import com.intellij.amper.lang.AmperLiteral
import com.intellij.amper.lang.AmperObject
import com.intellij.amper.lang.AmperProperty
import com.intellij.amper.lang.impl.allObjectElements
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiElement
import org.jetbrains.yaml.psi.YAMLKeyValue
import org.jetbrains.yaml.psi.YAMLMapping
import org.jetbrains.yaml.psi.YAMLScalar
import org.jetbrains.yaml.psi.YAMLSequence

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

class Sequence(override val sourceElement: PsiElement): AmperElementWrapper {
    companion object {
        fun from(element: PsiElement?) =
            when (element) {
                is YAMLSequence, is AmperObject -> Sequence(element)
                else -> null
            }
    }

    val items get() = when (sourceElement) {
        is YAMLSequence -> sourceElement.items.mapNotNull { it.value }
        is AmperObject -> sourceElement.allObjectElements.mapNotNull {
            if (it is AmperProperty && it.value == null) it.nameElement else null
        }
        else -> emptyList()
    }
}

class MappingNode(override val sourceElement: PsiElement): AmperElementWrapper {
    companion object {
        fun from(element: PsiElement?) =
            when (element) {
                is YAMLMapping, is AmperObject -> MappingNode(element)
                else -> null
            }
    }

    val psiElement = sourceElement.unwrapKey

    val keyValues get() = when (psiElement) {
        is YAMLMapping -> psiElement.keyValues
        is AmperObject -> psiElement.allObjectElements.filterIsInstance<AmperProperty>()
        else -> emptyList()
    }.map { MappingEntry(it) }

    /**
     * Try to find child node by given name.
     */
    fun tryGetChildNode(name: String): MappingEntry? {
        val altName = getAltName(name)
        return when (psiElement) {
            is YAMLMapping -> psiElement.keyValues.firstOrNull {
                it.keyText == name || it.keyText == altName
            }
            is AmperObject -> psiElement.allObjectElements.firstOrNull {
                (it as? AmperProperty)?.name == name ||
                        (it as? AmperProperty)?.name == altName
            }
            else -> null
        }?.let { MappingEntry(it) }
    }
}

internal fun getAltName(name: String) = name.split('-').mapIndexed { index, s ->
    if (index > 0) s.capitalize() else s
}.joinToString("")

class UnknownElementWrapper(override val sourceElement: PsiElement): AmperElementWrapper

class MappingEntry(override val sourceElement: PsiElement): AmperElementWrapper {

    val keyElement = when (sourceElement) {
        is YAMLKeyValue -> sourceElement.key
        is AmperProperty -> sourceElement.nameElement
        else -> null
    }

    val keyText get() = when (sourceElement) {
        is YAMLKeyValue -> sourceElement.keyText
        is AmperProperty -> sourceElement.name
        else -> null
    }

    val value get() = when (sourceElement) {
        is YAMLKeyValue -> sourceElement.value
        is AmperProperty -> sourceElement.value
        else -> null
    }
}