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
import org.jetbrains.amper.frontend.SchemaEnum
import org.jetbrains.amper.frontend.api.ImplicitConstructor
import org.jetbrains.amper.frontend.api.ImplicitConstructorParameter
import org.jetbrains.amper.frontend.api.SchemaNode
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
import org.jetbrains.amper.frontend.schema.CatalogDependency
import org.jetbrains.amper.frontend.schema.Dependency
import org.jetbrains.amper.frontend.schema.InternalDependency
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
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.hasAnnotation
import kotlin.reflect.full.isSubclassOf
import kotlin.reflect.full.isSubtypeOf
import kotlin.reflect.full.primaryConstructor
import kotlin.reflect.full.starProjectedType
import kotlin.reflect.full.withNullability

internal data class KeyWithContext(val key: Pointer, val contexts: Set<String>)

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

/*internal fun Set<Context>.isSubcontextOf(otherSet: Set<Context>): Boolean {
    return otherSet.flatMap { it.leaves }.containsAll(this.flatMap { it.leaves })
}

internal fun Map<KeyWithContext, Scalar>.getApplicableContexts(): List<Set<Context>> {
    return keys.map { it.contexts }.distinct()
}

internal fun Map<KeyWithContext, Scalar>.query(key: Pointer, contexts: Set<Context>): Scalar? {
    val applicableKeys = keys.filter { it.key == key && contexts.isSubcontextOf(it.contexts) }
    if (applicableKeys.isEmpty()) return null
    if (applicableKeys.size == 1) return this[applicableKeys.single()]
    return applicableKeys.sortedWith { o1, o2 -> o1.contexts.isSubcontextOf(o2.contexts).toInt() }
        .firstOrNull()?.let { this[it] }
}*/

class Pointer(val segmentName: String? = null,
                       var prev: Pointer? = null,
                       var next: Pointer? = null) {

    val firstSegment get() = run {
        var p: Pointer = this
        while (p.prev != null) p = p.prev!!
        p
    }

    operator fun plus(value: String): Pointer {
        if (segmentName == null && prev == null && next == null) {
            return Pointer(value)
        }

        val newPointer = Pointer(value, this)
        this.next = newPointer
        return newPointer
    }

    fun nextAfter(o: Pointer): Pointer? {
        var own: Pointer? = firstSegment
        var other: Pointer? = o.firstSegment
        while (other != null) {
            if (own == null || own.segmentName != other.segmentName) return null
            if (own === this || other === o) break
            own = own.next
            other = other.next
        }
        return own?.next
    }

    fun startsWith(o: Pointer): Boolean {
        var own: Pointer? = firstSegment
        var other: Pointer? = o.firstSegment
        while (other != null) {
            if (own == null || own.segmentName != other.segmentName) return false
            if (own === this || other === o) break
            own = own.next
            other = other.next
        }
        return true
    }

    override fun hashCode(): Int {
        return toString().hashCode()
    }

    override fun equals(o: Any?): Boolean {
        if (o !is Pointer || segmentName != o.segmentName) return false
        return toString() == o.toString()
    }

    override fun toString(): String {
        val builder = StringBuilder()
        val firstSegment1 = firstSegment
        var p: Pointer? = firstSegment1
        while (p != null) {
            if (p !== firstSegment1) builder.append(" :: ")
            builder.append(p.segmentName)
            if (p === this) break
            p = p.next
        }
        return builder.toString()
    }
}

context(Converter)
internal fun readTypedValue(type: KType,
                            table: Map<KeyWithContext, Scalar>,
                            path: Pointer,
                            contexts: Set<String>): Any? {
    if (type.withNullability(false) != type) {
        return readTypedValue(type.withNullability(false), table, path, contexts)
    }
    if (type.isSubtypeOf(TraceableEnum::class.starProjectedType)
        || type.isSubtypeOf(TraceableString::class.starProjectedType)
        || type.isSubtypeOf(TraceablePath::class.starProjectedType)) {
        val scalarValue = table.get(KeyWithContext(path, emptySet()))
        return when (type.unwrapKClass) {
            TraceableEnum::class -> type.arguments[0].type?.let { readTypedValue(it, table, path, contexts) }?.let {
                TraceableEnum::class.primaryConstructor!!.call(it).applyPsiTrace(scalarValue?.sourceElement)
            }
            TraceableString::class -> readTypedValue(String::class.starProjectedType, table, path, contexts)?.let {
                TraceableString(it as String).applyPsiTrace(scalarValue?.sourceElement)
            }
            TraceablePath::class -> readTypedValue(Path::class.starProjectedType, table, path, contexts)?.let {
                TraceablePath(it as Path).applyPsiTrace(scalarValue?.sourceElement)
            }
            else -> null
        }
    }
    val applicableKeys = table.keys.filter { it.key.startsWith(path) && it.contexts.containsAll(contexts) }
    if (applicableKeys.isEmpty()) return null

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
    if (type.isMap) {
        if (type.arguments[0].type?.isCollection == true) {
            return applicableKeys.map { it.contexts }
                .associate { it.map { TraceableString(it) }.toSet() to readTypedValue(type.mapValueType, table, path, it) }
        }
        return applicableKeys.associate {
            val name = if (
                table[it]?.sourceElement?.language is AmperLanguage
                || it.key.segmentName?.toIntOrNull() != null
              ) it.key.prev?.segmentName else it.key.segmentName
            name!! to readTypedValue(type.mapValueType, table, it.key.prev!!, contexts)
        }
    }
    if (type.isCollection)  {
        val visitedKeys = hashSetOf<Pointer>()
        return applicableKeys.mapNotNull {
            val newKey = it.key.nextAfter(path)
            if (newKey != null && !visitedKeys.any { newKey.startsWith(it) }) {
                visitedKeys.add(newKey)
                readTypedValue(type.collectionType, table, newKey, contexts)
            } else null
        }
    }

    // "enabled" shortcut
    if (type.isSubtypeOf(SchemaNode::class.starProjectedType)) {
        val scalarValue = table.get(KeyWithContext(path, contexts))
        val textValue = scalarValue?.textValue
        if (textValue != null) {
            // hack for internal and catalog dependencies, need to rethink this
            if (textValue.startsWith("./") && type.unwrapKClass == Dependency::class) {
                return InternalDependency().also { it.path = textValue.asAbsolutePath() }
            }
            if (textValue.startsWith("$") && type.unwrapKClass == Dependency::class) {
                return CatalogDependency().also { it.catalogKey = textValue }
            }
            // find the implicit constructor and invoke it
            val constructedType = type.unwrapKClass.findAnnotation<ImplicitConstructor>()?.constructedType
                ?: type.unwrapKClass

            val param = constructedType.schemaDeclaredMemberProperties()
                .filterIsInstance<KMutableProperty1<Any, Any?>>()
                .singleOrNull {
                    it.hasAnnotation<ImplicitConstructorParameter>()
                }

            if (param != null) {
                return type.instantiateType().also {
                    param.set(it, textValue)
                }
            }
        }
        if (textValue == "enabled") {
            val enabledProperty = type.unwrapKClass.schemaDeclaredMemberProperties()
                .filterIsInstance<KMutableProperty1<Any, Any?>>()
                .singleOrNull { it.name == "enabled" }
            if (enabledProperty != null) {
                return type.instantiateType().also {
                    enabledProperty.set(it, true)
                }
            }
        }
    }

    return type.instantiateType().also {
        readFromTable(it, table, path, contexts)
    }
}

context(Converter)
internal fun <T : Any> readFromTable(
    obj: T,
    table: Map<KeyWithContext, Scalar>,
    path: Pointer = Pointer(),
    contexts: Set<String> = emptySet()
) {
    obj::class.schemaDeclaredMemberProperties()
        .filterIsInstance<KMutableProperty1<Any, Any?>>()
        .forEach { prop ->
            readTypedValue(prop.returnType, table, path + prop.name, contexts)?.let {
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
    return kClass.findAnnotation<ImplicitConstructor>()?.constructedType?.createInstance()
        ?: kClass.createInstance()
}

private fun Boolean.toInt() = if (this) 0 else 1

open class AmperPsiAdapterVisitor {
    private val positionStack: Stack<String> = Stack()
    private val contextStack: Stack<Set<String>> = Stack()

    val position get() = positionStack.toList().let {
        var path = Pointer()
        for (item in it) {
            path += item
        }
        return@let path
    }
    val context get() = contextStack.toList().flatten().toSet()

    fun visitElement(element: PsiElement) {
        if (element.language is AmperLanguage) {
            object: AmperElementVisitor() {
                override fun visitContextBlock(o: AmperContextBlock) {
                    contextStack.push(o.contextNameList.mapNotNull { it.identifier?.text }.toSet())
                    super.visitContextBlock(o)
                    contextStack.pop()
                }

                override fun visitContextualStatement(o: AmperContextualStatement) {
                    contextStack.push(o.contextNameList.mapNotNull { it.identifier?.text }.toSet())
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
                    positionStack.push(
                        when (o.name) {
                            "testSettings" -> "test-settings"
                            "testDependencies" -> "test-dependencies"
                            null -> "[unnamed]"
                            else -> o.name
                        })
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
                        contextStack.push(keyValue.keyText.substring(atSign + 1).split('+').toSet())
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