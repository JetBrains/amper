/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.schemaConverter.psi.yaml

import com.intellij.amper.lang.AmperObject
import com.intellij.amper.lang.AmperProperty
import com.intellij.amper.lang.impl.allObjectElements
import com.intellij.psi.PsiElement
import org.jetbrains.yaml.psi.YAMLKeyValue
import org.jetbrains.yaml.psi.YAMLMapping
import org.jetbrains.yaml.psi.YAMLSequence

class Sequence(private val sourceElement: PsiElement) {
    companion object {
        fun from(element: PsiElement?) =
            when (element) {
                is YAMLSequence, is AmperObject -> Sequence(element)
                else -> null
            }
    }

    val items get() = when (sourceElement) {
        is YAMLSequence -> sourceElement.items.mapNotNull { it.value }
        is AmperObject -> sourceElement.allObjectElements.mapNotNull { it.asScalarNode() }
        else -> emptyList()
    }
}

class MappingNode(sourceElement: PsiElement) {
    companion object {
        fun from(element: PsiElement?) =
            when (element) {
                is YAMLMapping, is AmperObject -> MappingNode(element)
                else -> null
            }
    }

    private val psiElement = sourceElement.unwrapKey

    val keyValues get() = when (psiElement) {
        is YAMLMapping -> psiElement.keyValues
        is AmperObject -> psiElement.allObjectElements.filterIsInstance<AmperProperty>()
        else -> emptyList()
    }.map { MappingEntry(it) }

    /**
     * Try to find child node by given name.
     */
    fun tryGetChildNode(name: String): MappingEntry? {
        return when (psiElement) {
            is YAMLMapping -> psiElement.keyValues.firstOrNull { it.keyText == name }
            is AmperObject -> psiElement.allObjectElements.firstOrNull {
                (it as? AmperProperty)?.name == name
            }
            else -> null
        }?.let { MappingEntry(it) }
    }
}

class MappingEntry(val sourceElement: PsiElement) {

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