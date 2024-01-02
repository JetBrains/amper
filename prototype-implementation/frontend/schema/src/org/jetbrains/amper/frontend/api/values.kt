/*
 * Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.api

import org.jetbrains.amper.core.messages.ProblemReporterContext

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
    internal val allValues = mutableListOf<ValueBase<*>>()

    /**
     * Register a value.
     */
    fun <T : Any> value() = SchemaValue<T>().also { allValues.add(it) }

    /**
     * Register a nullable value.
     */
    fun <T : Any> nullableValue() = NullableSchemaValue<T>().also { allValues.add(it) }

    /**
     * Register a validator for this node.
     */
    context(ProblemReporterContext)
    open fun validate() {}
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
    protected var myValue: T? = null

    var default: Default<T>? = null

    abstract val value: T

    val unsafe: T? get() = myValue ?: default?.value

    val withoutDefault: T? get() = myValue

    open fun default(value: T) = apply { default = Default.Static(value) }

    open fun default(desc: String, getter: () -> T?) = apply { default = Default.Lambda(desc, getter) }

    /**
     * Overwrite current value, if provided value is not null.
     */
    operator fun invoke(newValue: T?): ValueBase<T> {
        if (newValue != null) {
            myValue = newValue
            if (newValue is Traceable) {
                trace = newValue.trace
            }
        }
        return this
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
    operator fun invoke(newValue: T?, onNull: () -> Unit): ValueBase<T> {
        if (newValue == null) onNull() else {
            myValue = newValue
            if (newValue is Traceable) {
                trace = newValue.trace
            }
        }
        return this
    }
}

/**
 * Optional (nullable) schema value.
 */
class NullableSchemaValue<T : Any> : ValueBase<T?>() {
    override val value: T? get() = unsafe

    override fun default(value: T?) = super.default(value) as NullableSchemaValue<T>
    override fun default(desc: String, getter: () -> T?) = super.default(desc, getter) as NullableSchemaValue<T>
}

/**
 * Visitor that invokes all validation handlers.
 */
context(ProblemReporterContext)
class ValidationVisitor: SchemaValuesVisitor() {
    override fun visitNode(it: SchemaNode): Unit? {
        with(it) { validate() }
        return super.visitNode(it)
    }
}

/**
 * Abstract class to traverse final schema tree.
 */
abstract class SchemaValuesVisitor {

    open fun visit(it: Any): Unit? = when(it) {
        is List<*> -> visitCollection(it)
        is Map<*, *> -> visitMap(it)
        is ValueBase<*> -> visitValue(it)
        is SchemaNode -> visitNode(it)
        else -> Unit
    }

    open fun visitCollection(it: Collection<*>): Unit? = it.filterNotNull().forEach { visit(it) }

    open fun visitMap(it: Map<*, *>): Unit? = visitCollection(it.values)

    open fun visitNode(it: SchemaNode): Unit? = it.allValues.forEach { visit(it) }

    open fun visitValue(it: ValueBase<*>): Unit? = it.withoutDefault?.let { visit(it) }
}