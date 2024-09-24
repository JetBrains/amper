/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.schemaConverter.psi

import com.intellij.amper.lang.AmperContextBlock
import com.intellij.amper.lang.AmperContextualStatement
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

internal data class KeyWithContext(val key: String, val contexts: Set<Context>)

internal fun PsiElement.readValueTable(): Map<KeyWithContext, Scalar> {
    val table = mutableMapOf<KeyWithContext, Scalar>()
    object : AmperPsiAdapterVisitor() {
        override fun visitScalar(node: Scalar) {
            table[KeyWithContext(position, context)] = node
            super.visitScalar(node)
        }
    }.visitElement(this)
    return table
}

internal fun Set<Context>.isSubcontextOf(otherSet: Set<Context>): Boolean {
    return otherSet.flatMap { it.leaves }.containsAll(this.flatMap { it.leaves })
}

internal fun Map<KeyWithContext, Scalar>.getApplicableContexts(): List<Set<Context>> {
    return keys.map { it.contexts }.distinct()
}

internal fun Map<KeyWithContext, Scalar>.query(key: String, contexts: Set<Context>): Scalar? {
    val applicableKeys = keys.filter { it.key == key && contexts.isSubcontextOf(it.contexts) }
    if (applicableKeys.isEmpty()) return null
    if (applicableKeys.size == 1) return this[applicableKeys.single()]
    return applicableKeys.sortedWith { o1, o2 -> o1.contexts.isSubcontextOf(o2.contexts).toInt() }
        .firstOrNull()?.let { this[it] }
}

private fun Boolean.toInt() = if (this) 0 else 1

open class AmperPsiAdapterVisitor {
    private val positionStack: Stack<String> = Stack()
    private val contextStack: Stack<Set<Context>> = Stack()

    val position get() = positionStack.toList().joinToString("/")
    val context get() = contextStack.toList().flatten().toSet()

    fun visitElement(element: PsiElement) {
        if (element.language is AmperLanguage) {
            object: AmperElementVisitor() {
                override fun visitContextBlock(o: AmperContextBlock) {
                    contextStack.push(o.contextNameList.mapNotNull { it.identifier?.text }
                        .mapNotNull { Platform[it] }.toSet())
                    super.visitContextBlock(o)
                    contextStack.pop()
                }

                override fun visitContextualStatement(o: AmperContextualStatement) {
                    contextStack.push(o.contextNameList.mapNotNull { it.identifier?.text }
                        .mapNotNull { Platform[it] }.toSet())
                    super.visitContextualStatement(o)
                    contextStack.pop()
                }

                override fun visitElement(o: PsiElement) {
                    ProgressIndicatorProvider.checkCanceled()
                    o.acceptChildren(this)
                }

                override fun visitObject(o: AmperObject) {
                    Sequence.from(o)?.let { visitSequence(it) }
                    MappingNode.from(o)?.let { visitMappingNode(it) }
                    super.visitObject(o)
                }

                override fun visitProperty(o: AmperProperty) {
                    positionStack.push(o.name ?: "unnamed")
                    visitMappingEntry(MappingEntry(o))
                    super.visitProperty(o)
                    positionStack.pop()
                }

                override fun visitLiteral(o: AmperLiteral) {
                    Scalar.from(o)?.let { visitScalar(it) }
                    super.visitLiteral(o)
                }
            }.visitElement(element)
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
                        positionStack.push(keyValue.keyText)
                    }
                    else {
                        positionStack.push(keyValue.keyText.substring(0, atSign))
                        contextStack.push(keyValue.keyText.substring(atSign).split('+')
                            .mapNotNull { Platform[it] }.toSet())
                    }
                    visitMappingEntry(MappingEntry(keyValue))
                    super.visitKeyValue(keyValue)
                    positionStack.pop()
                    if (atSign >= 0) { contextStack.pop() }
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

    open fun visitMappingNode(node: MappingNode) {}
    open fun visitMappingEntry(node: MappingEntry) {}
    open fun visitSequence(node: Sequence) {}
    open fun visitScalar(node: Scalar) {}
}