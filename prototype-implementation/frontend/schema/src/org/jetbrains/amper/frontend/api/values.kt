/*
 * Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.api

/**
 * Class to collect all values registered within it.
 */
abstract class SchemaBase : Traceable() {
    private val allValues = mutableListOf<ValueBase<*>>()

    /**
     * Register a value.
     */
    fun <T : Any> value(
        default: T? = null,
        doc: String? = null
    ) = SchemaValue<T>().apply { this.default = default }.also { allValues.add(it) }

    /**
     * Register a nullable value.
     */
    fun <T : Any> nullableValue(
        default: T? = null,
        doc: String? = null
    ) = NullableSchemaValue<T>().apply { this.default = default }.also { allValues.add(it) }
}

/**
 * Abstract value that can have a default value.
 */
sealed class ValueBase<T> : Traceable() {
    var default: T? = null
}

/**
 * Required (non-null) schema value.
 */
class SchemaValue<T : Any> : ValueBase<T>() {
    private var myValue: T? = null

    var value: T
        get() = myValue ?: default ?: error("No value")
        set(value) { myValue = value }

    operator fun invoke(newValue: T?) { myValue = newValue }
    operator fun invoke(newValue: T?, onNull: () -> Unit) {
        if (newValue == null) onNull() else myValue = newValue
    }
}

/**
 * Optional (nullable) schema value.
 */
class NullableSchemaValue<T : Any> : ValueBase<T>() {
    var value: T? = null
        get() = field ?: default

    operator fun invoke(newValue: T?) { value = newValue }
}