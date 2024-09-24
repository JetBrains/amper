/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.schemaConverter.psi

import com.intellij.amper.lang.AmperContextBlock
import com.intellij.amper.lang.AmperContextualStatement
import com.intellij.amper.lang.AmperElement
import com.intellij.amper.lang.AmperElementVisitor
import com.intellij.amper.lang.AmperLanguage
import com.intellij.amper.lang.AmperLiteral
import com.intellij.amper.lang.AmperObject
import com.intellij.amper.lang.AmperProperty
import com.intellij.openapi.progress.ProgressIndicatorProvider
import com.intellij.psi.PsiElement
import com.intellij.util.containers.Stack
import org.jetbrains.amper.frontend.Context
import org.jetbrains.amper.frontend.Platform
import org.jetbrains.yaml.psi.YAMLKeyValue
import org.jetbrains.yaml.psi.YAMLMapping
import org.jetbrains.yaml.psi.YAMLScalar
import org.jetbrains.yaml.psi.YAMLSequence
import org.jetbrains.yaml.psi.YamlPsiElementVisitor

class AmperPsiAdapterVisitor {
    val position: Stack<String> = Stack()
    val context: Stack<Set<Context>> = Stack()

    fun visitElement(element: PsiElement) {
        if (element.language is AmperLanguage) {
            object: AmperElementVisitor() {
                override fun visitContextBlock(o: AmperContextBlock) {
                    context.push(o.contextNameList.mapNotNull { it.identifier?.text }
                        .mapNotNull { Platform[it] }.toSet())
                    super.visitContextBlock(o)
                    context.pop()
                }

                override fun visitContextualStatement(o: AmperContextualStatement) {
                    context.push(o.contextNameList.mapNotNull { it.identifier?.text }
                        .mapNotNull { Platform[it] }.toSet())
                    super.visitContextualStatement(o)
                    context.pop()
                }

                override fun visitElement(o: AmperElement) {
                    ProgressIndicatorProvider.checkCanceled()
                    o.acceptChildren(this)
                }

                override fun visitObject(o: AmperObject) {
                    Sequence.from(o)?.let { visitSequence(it) }
                        ?: MappingNode.from(o)?.let { visitMappingNode(it) }
                    super.visitObject(o)
                }

                override fun visitProperty(o: AmperProperty) {
                    position.push(o.name ?: "unnamed")
                    visitMappingEntry(MappingEntry(o))
                    super.visitProperty(o)
                    position.pop()
                }

                override fun visitLiteral(o: AmperLiteral) {
                    Scalar.from(o)?.let { visitScalar(it) }
                    super.visitLiteral(o)
                }
            }.visitPsiElement(element)
        }
        else {
            object : YamlPsiElementVisitor() {
                override fun visitElement(element: PsiElement) {
                    ProgressIndicatorProvider.checkCanceled()
                    element.acceptChildren(this)
                }

                override fun visitMapping(mapping: YAMLMapping) {
                    visitMappingNode(MappingNode.from(mapping)!!)
                    super.visitMapping(mapping)
                }

                override fun visitKeyValue(keyValue: YAMLKeyValue) {
                    val atSign = keyValue.keyText.indexOf('@')
                    if (atSign < 0) {
                        position.push(keyValue.keyText)
                    }
                    else {
                        position.push(keyValue.keyText.substring(0, atSign))
                        context.push(keyValue.keyText.substring(atSign).split('+')
                            .mapNotNull { Platform[it] }.toSet())
                    }
                    visitMappingEntry(MappingEntry(keyValue))
                    super.visitKeyValue(keyValue)
                    position.pop()
                    if (atSign >= 0) { context.pop() }
                }

                override fun visitSequence(sequence: YAMLSequence) {
                    visitSequence(Sequence(sequence))
                    super.visitSequence(sequence)
                }

                override fun visitScalar(scalar: YAMLScalar) {
                    Scalar.from(scalar)?.let { visitScalar(it) }
                    super.visitScalar(scalar)
                }
            }.visitElement(element)
        }
    }

    fun visitMappingNode(node: MappingNode) {}
    fun visitMappingEntry(node: MappingEntry) {}
    fun visitSequence(node: Sequence) {}
    fun visitScalar(node: Scalar) {}
}