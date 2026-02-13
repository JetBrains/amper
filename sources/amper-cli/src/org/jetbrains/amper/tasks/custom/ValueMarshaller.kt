/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.tasks.custom

import com.android.utils.associateByNotNull
import org.jetbrains.amper.frontend.api.toStableJsonLikeString
import org.jetbrains.amper.frontend.tree.BooleanNode
import org.jetbrains.amper.frontend.tree.CompleteListNode
import org.jetbrains.amper.frontend.tree.CompleteMapNode
import org.jetbrains.amper.frontend.tree.CompleteObjectNode
import org.jetbrains.amper.frontend.tree.CompleteTreeNode
import org.jetbrains.amper.frontend.tree.EnumNode
import org.jetbrains.amper.frontend.tree.IntNode
import org.jetbrains.amper.frontend.tree.NullLiteralNode
import org.jetbrains.amper.frontend.tree.PathNode
import org.jetbrains.amper.frontend.tree.StringNode
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import java.util.*
import kotlin.reflect.full.memberProperties
import kotlin.reflect.jvm.javaMethod

/**
 * A helper class responsible for converting the objects from the internal schema domain to the public
 * (extensibility-api) schema domain.
 *
 * The implementation is thread-safe.
 *
 * Preserves converted values' identities when marshaling the same value multiple times.
 */
class ValueMarshaller(
    private val publicClassLoader: ClassLoader,
) {
    private val valueCache = IdentityHashMap<CompleteTreeNode, Any?>()

    fun marshallValue(value: CompleteTreeNode): Any? {
        return when (value) {
            is NullLiteralNode -> null
            is BooleanNode -> value.value
            is IntNode -> value.value
            is PathNode -> value.value
            is StringNode -> value.value
            is EnumNode -> withValueCache(value) {
                val enumClass = publicClassLoader.loadClass(value.type.declaration.publicInterfaceReflectionName)
                enumClass.enumConstants.first { (it as Enum<*>).name == value.entryName }
            }
            is CompleteListNode -> withValueCache(value) {
                value.children.map(::marshallValue)
            }
            is CompleteMapNode -> withValueCache(value) {
                value.refinedChildren.mapValues { (_, v) -> marshallValue(v.value) }
            }
            is CompleteObjectNode -> withValueCache(value) {
                createProxy(
                    value = value,
                    interfaceName = value.type.declaration.publicInterfaceReflectionName
                        ?: error("No public interface reflection name for $value"),
                )
            }
        }
    }

    private fun createProxy(
        value: CompleteObjectNode,
        interfaceName: String,
    ): Any {
        val publicInterfaceClass = publicClassLoader.loadClass(interfaceName)
        val publicMethodsToProperties = publicInterfaceClass.kotlin.memberProperties.associateByNotNull {
            it.getter.javaMethod
        }
        val internalPropertiesByName = value.instance.javaClass.kotlin.memberProperties.associateByNotNull { it.name }
        val handler = InvocationHandler { proxy: Any, method: Method, args: Array<out Any?>? ->
            when (method.name) {
                "toString" -> value.toStableJsonLikeString()
                "hashCode" -> value.hashCode()
                "equals" -> args?.get(0) === proxy
                else -> {
                    val property = publicMethodsToProperties[method]
                        ?: error("Unhandled method $method: not a property getter")
                    val keyValue = value.refinedChildren[property.name]
                    if (keyValue != null) {
                        marshallValue(keyValue.value)
                    } else {
                        // Public property may be backed by an internal one for @Provided things.
                        val internalProperty = internalPropertiesByName[property.name]
                            ?: error("Unhandled method $method: no value and no backing property found")
                        internalProperty.get(value.instance)
                    }
                }
            }
        }
        return Proxy.newProxyInstance(publicClassLoader, arrayOf(publicInterfaceClass), handler)
    }

    private fun withValueCache(value: CompleteTreeNode, mapping: () -> Any?): Any? = synchronized(valueCache) {
        valueCache.getOrPut(value) { mapping() }
    }
}