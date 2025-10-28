/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.tasks.custom

import com.android.utils.associateByNotNull
import org.jetbrains.amper.frontend.api.SchemaNode
import org.jetbrains.amper.frontend.api.TraceableValue
import org.jetbrains.amper.frontend.api.toStableJsonLikeString
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Proxy
import java.lang.reflect.Type
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
    private val valueCache = IdentityHashMap<Any?, Any?>()

    fun marshallValue(value: Any?, type: Type): Any? {
        return when (value) {
            is String if type is Class<*> && type.isEnum -> {
                // Do not use valueCache on String instances
                // and because resulting enum constants already preserve identity when called multiple times.
                type.enumConstants.first { (it as Enum<*>).name == value }
            }
            is SchemaNode -> withValueCache(value) {
                val publicInterfaceName = value.schemaType.publicInterfaceReflectionName
                    ?: error("No public interface reflection name for $value")
                createProxy(value, publicInterfaceName)
            }
            is List<*> -> withValueCache(value) {
                value.map { marshallValue(it, (type as ParameterizedType).actualTypeArguments.first()) }
            }
            is Map<*, *> -> withValueCache(value) {
                value.mapValues { marshallValue(it.value, (type as ParameterizedType).actualTypeArguments.last()) }
            }
            is TraceableValue<*> -> value.value
            else -> value
        }
    }

    private fun createProxy(
        value: SchemaNode,
        interfaceName: String,
    ): Any {
        val publicInterfaceClass = publicClassLoader.loadClass(interfaceName)
        val publicMethodsToProperties = publicInterfaceClass.kotlin.memberProperties.associateByNotNull {
            it.getter.javaMethod
        }
        val internalPropertiesByName = value.javaClass.kotlin.memberProperties.associateByNotNull { it.name }
        val handler = InvocationHandler { proxy: Any, method: Method, args: Array<out Any?>? ->
            when (method.name) {
                "toString" -> value.toStableJsonLikeString()
                "hashCode" -> value.hashCode()
                "equals" -> args?.get(0) === proxy
                else -> {
                    val property = publicMethodsToProperties[method]
                        ?: error("Unhandled method $method: not a property getter")
                    val valueHolder = value.valueHolders[property.name]
                    if (valueHolder != null) {
                        marshallValue(valueHolder.value, method.genericReturnType)
                    } else {
                        // Public property may be backed by an internal one for @Provided things.
                        val internalProperty = internalPropertiesByName[property.name]
                            ?: error("Unhandled method $method: no value and no backing property found")
                        internalProperty.get(value)
                    }
                }
            }
        }
        return Proxy.newProxyInstance(publicClassLoader, arrayOf(publicInterfaceClass), handler)
    }

    private fun <T> withValueCache(value: T, mapping: (T) -> Any?): Any? = synchronized(valueCache) {
        valueCache.getOrPut(value) { mapping(value) }
    }
}