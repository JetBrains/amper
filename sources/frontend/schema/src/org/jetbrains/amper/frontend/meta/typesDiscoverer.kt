/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.meta

import org.jetbrains.amper.frontend.api.PropertyMeta
import org.jetbrains.amper.frontend.api.valueBase
import org.jetbrains.amper.frontend.types.allSchemaProperties
import org.jetbrains.amper.frontend.types.isCollection
import org.jetbrains.amper.frontend.types.isMap
import org.jetbrains.amper.frontend.types.isScalar
import org.jetbrains.amper.frontend.types.isSchemaNode
import org.jetbrains.amper.frontend.types.isSealed
import org.jetbrains.amper.frontend.types.isTraceablePath
import org.jetbrains.amper.frontend.types.kClass
import org.jetbrains.amper.frontend.types.allSealedSubclasses
import org.jetbrains.amper.frontend.schema.Settings
import org.jetbrains.amper.frontend.types.ATypes
import org.jetbrains.amper.frontend.types.isEnum
import org.jetbrains.amper.frontend.types.isTraceableEnum
import java.util.Properties
import kotlin.reflect.KClass
import kotlin.reflect.KType
import kotlin.reflect.full.createInstance
import kotlin.reflect.full.starProjectedType
import kotlin.reflect.full.withNullability

val ATypesDiscoverer = DefaultATypesDiscoverer()

/**
 * Discovering all schema-defined properties and types.
 */
open class DefaultATypesDiscoverer : ATypes() {
    private val discoveredTypes = mutableMapOf<KType, AType>()

    private val amperCL = this::class.java.classLoader

    /**
     * Load [AType] for specified [KType].
     */
    override fun get(type: KType): AType = discoveredTypes.computeIfAbsent(type) {
        when {
            type.isEnum || type.isTraceableEnum -> AEnum(type)
            type.isTraceablePath -> AScalar(type)
            type.isScalar -> AScalar(type)
            type.isMap -> AMap(type)
            type.isCollection -> AList(type)
            type.isSchemaNode && type.isSealed -> APolymorphic(type) { polyInheritors(type) }
            type.isSchemaNode -> AObject(type) { type.allSchemaProperties() }
            else -> error("Unsupported type: $type")
        }
    }

    /**
     * Get locally declared and custom (from plugins) properties for type.
     */
    private fun KType.allSchemaProperties() = localSchemaProperties() + customProperties(this)

    private fun KType.localSchemaProperties() = kClass.createInstance().let { instance ->
        kClass.allSchemaProperties().map { PropertyMeta(it, it.valueBase(instance)?.default) }
    }

    protected open fun polyInheritors(type: KType) =
        type.allSealedSubclasses.map(KClass<*>::starProjectedType)

    /**
     * Load custom (plugin) properties for type.
     *
     * Note: [type] is not a receiver to have a possibility to call `super.customProperties()`.
     */
    protected open fun customProperties(type: KType): List<PropertyMeta> =
        if (type.kClass != Settings::class) emptyList()
        else amperCL.getResources("META-INF/amper/plugin.properties").toList().mapNotNull out@{
            val props = Properties().apply { it.openStream().use(::load) }
            val pluginSchemaId = props.getProperty("org.jetbrains.amper.plugin.id") ?: return@out null
            val pluginSchemaFQN = props.getProperty("org.jetbrains.amper.plugin.schema") ?: return@out null
            val javaSchema = runCatching { amperCL.loadClass(pluginSchemaFQN) }.getOrNull()
            val schemaKType = javaSchema?.kotlin?.starProjectedType?.withNullability(true) ?: return@out null
            PropertyMeta(pluginSchemaId, schemaKType)
        }
}