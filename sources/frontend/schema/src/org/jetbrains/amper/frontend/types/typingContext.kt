/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.types

import org.jetbrains.amper.frontend.api.TraceablePath
import org.jetbrains.amper.frontend.api.TraceableString
import org.jetbrains.amper.frontend.api.TraceableValue
import org.jetbrains.amper.frontend.types.SchemaType.TypeWithDeclaration
import java.nio.file.Path
import kotlin.reflect.KClass
import kotlin.reflect.KType
import kotlin.reflect.full.isSubclassOf
import kotlin.reflect.jvm.javaType

/**
 * Container for [SchemaType]s and [SchemaTypeDeclaration]s.
 */
abstract class SchemaTypingContext protected constructor(
    private val parent: SchemaTypingContext? = null
) {

    /**
     * Searches for [SchemaType] for given [type] declaration within parent context first and then within this context..
     */
    fun getType(type: KType): SchemaType = getAsTypeWithoutDeclaration(type)
        ?: parent?.findTypeWithDeclarationSelfOnly(type)
        ?: findTypeWithDeclarationSelfOnly(type)
        ?: error("No schema type type for ${type.javaType.typeName}")
    
    fun getDeclaration(key: DeclarationKey) = parent?.findDeclarationSelfOnly(key) 
        ?: findDeclarationSelfOnly(key)
        ?: error("No schema type declaration for $key")
    
    /**
     * Searches for [SchemaType] for given [type] declaration within this context only.
     */
    protected abstract fun findTypeWithDeclarationSelfOnly(type: KType): TypeWithDeclaration?

    /**
     * Searches for [SchemaTypeDeclaration] for given [key] declaration within this context only.
     */
    protected abstract fun findDeclarationSelfOnly(key: DeclarationKey): SchemaTypeDeclaration?

    /**
     * Tries to interpret given [type] as a simple type (non enum scalars, collections).
     * Returns `null` if the type is not simple.
     */
    private fun getAsTypeWithoutDeclaration(type: KType): SchemaType? {
        val classifier = type.classifier
        val isTraceable = classifier is KClass<*> && classifier.isSubclassOf(TraceableValue::class)
        return when (classifier) {
            Int::class -> SchemaType.IntType(
                isMarkedNullable = type.isMarkedNullable
            )
            Boolean::class -> SchemaType.BooleanType(
                isMarkedNullable = type.isMarkedNullable
            )
            Path::class, TraceablePath::class -> SchemaType.PathType(
                isMarkedNullable = type.isMarkedNullable,
                isTraceableWrapped = isTraceable,
            )
            String::class, TraceableString::class -> SchemaType.StringType(
                isMarkedNullable = type.isMarkedNullable,
                isTraceableWrapped = isTraceable,
            )
            Map::class -> SchemaType.MapType(
                isMarkedNullable = type.isMarkedNullable,
                keyType = getType(checkNotNull(type.arguments[0].type)) as SchemaType.StringType,
                valueType = getType(checkNotNull(type.arguments[1].type)),
            )
            List::class -> SchemaType.ListType(
                isMarkedNullable = type.isMarkedNullable,
                elementType = getType(checkNotNull(type.arguments[0].type)),
            )
            else -> null
        }
    }
}

interface DeclarationKey