/*
 * Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.api

/**
 * A class, that every enum, participating in
 * schema building should inherit.
 */
interface SchemaEnum {
    val schemaValue: String
}

/**
 * Class to collect all values registered within it.
 */
abstract class SchemaNode : Traceable() {
    private val allValues = mutableListOf<ValueBase<*>>()

    /**
     * Register a value.
     */
    fun <T : Any> value() = SchemaValue<T>().also { allValues.add(it) }

    /**
     * Register a nullable value.
     */
    fun <T : Any> nullableValue() = NullableSchemaValue<T>().also { allValues.add(it) }
}

sealed class Default<T> {
    abstract val value: T?

    data class Static<T>(override val value: T) : Default<T>()
    data class Lambda<T>(val desc: String, private val getter: () -> T?) : Default<T>() {
        override val value by lazy { getter() }
    }
}

/**
 * Abstract value that can have a default value.
 */
sealed class ValueBase<T> : Traceable() {
    var default: Default<T>? = null

    protected var myValue: T? = null

    abstract val value: T

    val withoutDefault: T? get() = myValue

    open fun default(value: T) = apply { default = Default.Static(value) }

    open fun default(desc: String, getter: () -> T?) = apply { default = Default.Lambda(desc, getter) }

    /**
     * Overwrite current value, if provided value is not null.
     */
    operator fun invoke(newValue: T?) {
        if (newValue != null) myValue = newValue
    }
}

/**
 * Required (non-null) schema value.
 */
class SchemaValue<T : Any> : ValueBase<T>() {
    override val value: T
        get() = myValue ?: default?.value ?: error("No value")

    override fun default(value: T) = super.default(value) as SchemaValue<T>
    override fun default(desc: String, getter: () -> T?) = super.default(desc, getter) as SchemaValue<T>

    /**
     * Overwrite current value, if provided value is not null.
     * Invoke [onNull] if it is.
     */
    operator fun invoke(newValue: T?, onNull: () -> Unit) {
        if (newValue == null) onNull() else myValue = newValue
    }
}

/**
 * Optional (nullable) schema value.
 */
class NullableSchemaValue<T : Any> : ValueBase<T?>() {
    override val value: T? get() = myValue ?: default?.value
}