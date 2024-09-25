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
import com.intellij.amper.lang.impl.propertyList
import com.intellij.openapi.progress.ProgressIndicatorProvider
import com.intellij.psi.PsiElement
import com.intellij.util.containers.Stack
import org.jetbrains.amper.frontend.Context
import org.jetbrains.amper.frontend.Platform
import org.jetbrains.amper.frontend.SchemaEnum
import org.jetbrains.amper.frontend.api.TraceableEnum
import org.jetbrains.amper.frontend.api.TraceablePath
import org.jetbrains.amper.frontend.api.TraceableString
import org.jetbrains.amper.frontend.api.applyPsiTrace
import org.jetbrains.amper.frontend.builders.collectionType
import org.jetbrains.amper.frontend.builders.isBoolean
import org.jetbrains.amper.frontend.builders.isCollection
import org.jetbrains.amper.frontend.builders.isInt
import org.jetbrains.amper.frontend.builders.isMap
import org.jetbrains.amper.frontend.builders.isPath
import org.jetbrains.amper.frontend.builders.isScalar
import org.jetbrains.amper.frontend.builders.isString
import org.jetbrains.amper.frontend.builders.mapValueType
import org.jetbrains.amper.frontend.builders.schemaDeclaredMemberProperties
import org.jetbrains.amper.frontend.builders.unwrapKClass
import org.jetbrains.yaml.psi.YAMLKeyValue
import org.jetbrains.yaml.psi.YAMLMapping
import org.jetbrains.yaml.psi.YAMLScalar
import org.jetbrains.yaml.psi.YAMLSequence
import org.jetbrains.yaml.psi.YamlPsiElementVisitor
import java.nio.file.Path
import kotlin.reflect.KMutableProperty1
import kotlin.reflect.KType
import kotlin.reflect.full.createInstance
import kotlin.reflect.full.declaredFunctions
import kotlin.reflect.full.isSubclassOf
import kotlin.reflect.full.isSubtypeOf
import kotlin.reflect.full.primaryConstructor
import kotlin.reflect.full.starProjectedType

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

context(Converter)
internal fun readTypedValue(type: KType,
                            table: Map<KeyWithContext, Scalar>,
                            path: String): Any? {
    if (type.isSubtypeOf(TraceableEnum::class.starProjectedType)
        || type.isSubtypeOf(TraceableString::class.starProjectedType)
        || type.isSubtypeOf(TraceablePath::class.starProjectedType)) {
        val scalarValue = table.get(KeyWithContext(path, emptySet()))
        return when (type.unwrapKClass) {
            TraceableEnum::class -> type.arguments[0].type?.let { readTypedValue(it, table, path) }?.let {
                TraceableEnum::class.primaryConstructor!!.call(it).applyPsiTrace(scalarValue?.sourceElement)
            }
            TraceableString::class -> type.arguments[0].type?.let { readTypedValue(it, table, path) }?.let {
                TraceableString(it as String).applyPsiTrace(scalarValue?.sourceElement)
            }
            TraceablePath::class -> type.arguments[0].type?.let { readTypedValue(it, table, path) }?.let {
                TraceablePath(it as Path).applyPsiTrace(scalarValue?.sourceElement)
            }
            else -> null
        }
    }
    if (type.isScalar) {
        val scalarValue = table.get(KeyWithContext(path, emptySet()))
        val text = scalarValue?.textValue ?: return null
        when {
            type.isSubtypeOf(SchemaEnum::class.starProjectedType) -> {
                (type.unwrapKClass.declaredFunctions.firstOrNull {
                    it.name == "values"
                }?.call() as? Array<SchemaEnum>)?.firstOrNull { it.schemaValue == scalarValue?.textValue }?.let {
                    return it
                    //prop.set(obj, it/*TraceableEnum(it).also{ it.trace = scalarValue?.sourceElement?.let { PsiTrace(it) } }*/)
                }
            }
            type.isString -> return text
            type.isBoolean -> return text == "true"
            type.isInt -> return text.toInt()
            type.isPath -> return text.asAbsolutePath()
        }
    }
    val applicableKeys = table.keys.filter { it.key.startsWith("$path/") }
    if (type.isMap) {
        return applicableKeys.associate {
            val nextSlash = it.key.indexOf('/', "$path/".length + 1)
            val key = if (nextSlash >= 0) it.key.substring(0, nextSlash) else it.key
            key.substring("$path/".length, if (nextSlash >= 0) nextSlash else key.length) to readTypedValue(type.mapValueType, table, key)
        }
    }
    if (type.isCollection)  {
        return applicableKeys.mapNotNull {
            val nextSlash = it.key.indexOf('/', "$path/".length + 1)
            val key = if (nextSlash >= 0) it.key.substring(0, nextSlash) else it.key
            readTypedValue(type.collectionType, table, key)
        }
    }
    //if (type.isSubtypeOf(SchemaNode::class.starProjectedType)) {
        return type.instantiateType().also {
            readFromTable(it, table, path)
        }
    //}
}

context(Converter)
internal fun <T : Any> readFromTable(
    obj: T,
    table: Map<KeyWithContext, Scalar>,
    path: String = ""
) {
    obj::class.schemaDeclaredMemberProperties()
        .filterIsInstance<KMutableProperty1<Any, Any?>>()
        .forEach { prop ->
            val newPath = if (path.isEmpty()) prop.name else path + "/" + prop.name
            readTypedValue(prop.returnType, table, newPath)?.let {
                prop.set(obj, it)
            }
    }
}

private fun KType.instantiateType(): Any {
    val kClass = unwrapKClass
    if (kClass.isSubclassOf(Set::class)) {
        return HashSet<Any>()
    }
    if (kClass.isSubclassOf(List::class)) {
        return ArrayList<Any>()
    }
    return kClass.createInstance()
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
                    if (o.propertyList.any { it.value == null }) {
                        Sequence.from(o)?.let { visitSequence(it) }
                        o.propertyList.forEachIndexed { index, amperProperty ->
                            positionStack.push(index.toString())
                            amperProperty.accept(this)
                            positionStack.pop()
                        }
                        // do not call super here!
                    }
                    else {
                        MappingNode.from(o)?.let { visitMappingNode(it) }
                        super.visitObject(o)
                    }
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
                    sequence.items.forEachIndexed { index, item ->
                        positionStack.push(index.toString())
                        item.value?.accept(this)
                        positionStack.pop()
                    }
                    // do not call super here!
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