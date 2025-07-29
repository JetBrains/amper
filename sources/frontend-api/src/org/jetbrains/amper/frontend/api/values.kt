/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.api

import com.intellij.openapi.util.Key
import com.intellij.psi.PsiElement
import com.intellij.util.asSafely
import java.nio.file.Path
import kotlin.properties.PropertyDelegateProvider
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty
import kotlin.reflect.KProperty0
import kotlin.reflect.KProperty1
import kotlin.reflect.jvm.isAccessible

/**
 * Key, pointing to schema value, that was connected to [PsiElement].
 */
val linkedAmperValue = Key.create<ValueDelegateBase<*>>("org.jetbrains.amper.frontend.linkedValue")

/**
 * Key, pointing to schema enum value, that was connected to [PsiElement].
 */
val linkedAmperEnumValue = Key.create<Enum<*>>("org.jetbrains.amper.frontend.linkedEnumValue")

/**
 * Key, pointing to schema node, that was connected to [PsiElement].
 */
val linkedAmperNode = Key.create<SchemaNode>("org.jetbrains.amper.frontend.linkedNode")

typealias ValueHolders = MutableMap<String, ValueHolder<*>>

data class ValueHolder<T>(
    val value: T,
    val trace: Trace? = null,
)

@RequiresOptIn
annotation class InternalTraceSetter

/**
 * Class to collect all values registered within it.
 */
abstract class SchemaNode : Traceable {
    @IgnoreForSchema
    internal val allValues = mutableListOf<ValueDelegateBase<*>>()
    @IgnoreForSchema
    val valueHolders: ValueHolders = mutableMapOf()

    /**
     * Register a value.
     */
    fun <T : Any> value() = SchemaValueProvider<T, SchemaValue<T>>(::SchemaValue)

    /**
     * Register a value with a default.
     */
    fun <T : Any> value(default: T) = SchemaValueProvider(::SchemaValue, Default.Static(default))

    /**
     * Register a value with a default depending on another property
     */
    fun <T : Any> dependentValue(
        property: KProperty0<T>
    ) = SchemaValueProvider(::SchemaValue, Default.Dependent<T, T>("Inherited from '${property.name}'", property))

    /**
     * Register a value with a default depending on another property
     */
    fun <T, V : Any> dependentValue(
        property: KProperty0<T>,
        desc: String? = null,
        transformValue: (value: T?) -> V?
    ) = SchemaValueProvider(
        ::SchemaValue,
        Default.Dependent(desc ?: "Computed from '${property.name}'", property, transformValue)
    )

    /**
     * Register a value with a lazy default.
     */
    // the default value is nullable to allow performing validation without crashing (using "unsafe" access)
    fun <T : Any> value(default: () -> T?) = SchemaValueProvider(::SchemaValue, Default.Lambda(desc = null, default))

    /**
     * Register a nullable value.
     */
    fun <T : Any> nullableValue() = SchemaValueProvider<T, NullableSchemaValue<T>>(::NullableSchemaValue)

    /**
     * Register a nullable value with a default.
     */
    fun <T : Any> nullableValue(default: T?) =
        SchemaValueProvider<T, NullableSchemaValue<T>>(::NullableSchemaValue, default?.let { Default.Static(it) })

    /**
     * Register a nullable value with a lazy default.
     */
    fun <T : Any> nullableValue(desc: String? = null, default: () -> T?) =
        SchemaValueProvider(::NullableSchemaValue, Default.Lambda(desc = desc, default))

    @IgnoreForSchema
    final override lateinit var trace: Trace
        private set

    // we have to use a setter method because custom var setters are not allowed for lateinit vars
    @InternalTraceSetter
    fun setTrace(trace: Trace) {
        if (trace is PsiTrace) {
            trace.psiElement.putUserData(linkedAmperNode, this)
        }
        this.trace = trace
    }
}

sealed class Default<out T> {
    abstract val value: T?

    data class Static<T>(override val value: T) : Default<T>()

    data class Lambda<T>(val desc: String?, private val getter: () -> T?) : Default<T>() {
        override val value by lazy { getter() }
    }

    data class Dependent<T, V>(
        val desc: String,
        val property: KProperty0<T>,
        val transformValue: ((T?) -> V?)? = null
    ) : Default<V>() {
        override val value by lazy { transformValue?.invoke(property.withoutDefault) }
        val isReference = transformValue == null
    }
}

class SchemaValueProvider<T : Any, VT : ValueDelegateBase<*>>(
    val ctor: (KProperty<*>, Default<T>?, ValueHolders) -> VT,
    val default: Default<T>? = null,
) : PropertyDelegateProvider<SchemaNode, VT> {
    override fun provideDelegate(thisRef: SchemaNode, property: KProperty<*>): VT {
        // Make sure that we can access delegates from reflection.
        property.isAccessible = true
        return ctor(property, default, thisRef.valueHolders).also { thisRef.allValues.add(it) }
    }
}

/**
 * Abstract value that can have a default value.
 */
sealed class ValueDelegateBase<T>(
    val property: KProperty<*>,
    val default: Default<T>?,
    valueHolders: ValueHolders,
) : Traceable, ReadWriteProperty<SchemaNode, T> {
    // We are creating lambdas here to prevent misusage of [valueHolders] from [ValueDelegateBase].
    private val valueGetter: () -> ValueHolder<T>? = { valueHolders[property.name] as ValueHolder<T>? }
    private val valueSetter: (ValueHolder<T>?) -> Unit = { if (it != null) valueHolders[property.name] = it }

    abstract val value: T
    val unsafe: T? get() = valueGetter()?.value ?: default?.value
    val withoutDefault: T? get() = valueGetter()?.value

    override fun getValue(thisRef: SchemaNode, property: KProperty<*>) = value
    override fun setValue(thisRef: SchemaNode, property: KProperty<*>, value: T) {
        if (value != null) {
            valueSetter(ValueHolder(value, value.asSafely<Traceable>()?.trace))
        }
    }

    override val trace: Trace?
        get() = valueGetter()?.trace
            ?: default.asSafely<Default.Dependent<*, *>>()?.property?.setAccessible()?.valueBaseOrNull?.let(::DefaultTrace)

    override fun toString(): String = "SchemaValue(property = $property, value = $value)"
}

private fun <T : KProperty<*>> T.setAccessible() = apply { isAccessible = true }

@Suppress("UNCHECKED_CAST")
fun <T, V> KProperty1<T, V>.valueBase(receiver: T): ValueDelegateBase<V>? =
    setAccessible().getDelegate(receiver) as? ValueDelegateBase<V>

/**
 * Returns the traceable [ValueDelegateBase] of this property, or throws if this property isn't defined with such
 * delegate. This should be used when this property is a schema property defined with a schema delegate.
 *
 * When in doubt, use [valueBaseOrNull] instead to handle the case when there is no delegate.
 */
@Suppress("UNCHECKED_CAST")
val <T> KProperty0<T>.valueBase: ValueDelegateBase<T>
    get() = setAccessible().getDelegate() as? ValueDelegateBase<T>
        ?: error("Property $this should have a traceable schema delegate")

@Suppress("UNCHECKED_CAST")
val <T> KProperty0<T>.valueBaseOrNull: ValueDelegateBase<T>?
    get() = setAccessible().getDelegate() as? ValueDelegateBase<T>

val <T> KProperty0<T>.withoutDefault: T? get() = valueBaseOrNull?.let { return it.withoutDefault } ?: get()

val <T> KProperty0<T>.isDefault get() = valueBaseOrNull?.trace is DefaultTrace

val <T> KProperty0<T>.unsafe: T? get() = valueBaseOrNull?.let { return it.unsafe } ?: get()

/**
 * Required (non-null) schema value.
 */
class SchemaValue<T : Any>(
    property: KProperty<*>,
    default: Default<T>?,
    valueHolders: ValueHolders,
) : ValueDelegateBase<T>(property, default, valueHolders) {
    override val value: T get() = unsafe ?: error("No value")
}

/**
 * Optional (nullable) schema value.
 */
class NullableSchemaValue<T : Any>(
    property: KProperty<*>,
    default: Default<T?>?,
    valueHolders: ValueHolders,
) : ValueDelegateBase<T?>(property, default, valueHolders) {
    override val value: T? get() = unsafe
}

/**
 * Abstract class to traverse final schema tree.
 */
abstract class SchemaValuesVisitor {

    open fun visit(it: Any?) {
        when (it) {
            is Collection<*> -> visitCollection(it)
            is Map<*, *> -> visitMap(it)
            is ValueDelegateBase<*> -> visitValue(it)
            is SchemaNode -> visitNode(it)
            else -> visitOther(it)
        }
    }

    open fun visitCollection(it: Collection<*>) {
        it.filterNotNull().forEach { visit(it) }
    }

    open fun visitMap(it: Map<*, *>) {
        visitCollection(it.values)
    }

    open fun visitNode(it: SchemaNode) {
        it.allValues.sortedBy { it.property.name }.forEach { visit(it) }
    }

    open fun visitValue(it: ValueDelegateBase<*>) {
        it.withoutDefault?.let { visit(it) }
    }

    open fun visitOther(it: Any?) = Unit
}

/**
 * Visitor that is aware of visited properties' path.
 *
 * **Warning!** It is relying on the fact that visiting is done in linear non-parallel way.
 */
abstract class PathAwareSchemaValuesVisitor : SchemaValuesVisitor() {
    private val path: MutableList<ValueDelegateBase<*>> = mutableListOf()
    val currentPath: List<ValueDelegateBase<*>> get() = path
    override fun visitValue(it: ValueDelegateBase<*>) = try {
        path.add(it)
        super.visitValue(it)
    } finally {
        path.removeLast()
    }
}

/**
 * When the enum value isn't wrapped into the schema value (e.g., in a collection or in AOM),
 * it's impossible to determine the trace of that enum.
 *
 * This wrapper allows persisting a trace in such scenarios.
 */
class TraceableEnum<T : Enum<*>>(value: T, trace: Trace) : TraceableValue<T>(value, trace) {

    init {
        if (trace is PsiTrace) trace.psiElement.putUserData(linkedAmperEnumValue, this.value)
    }

    override fun toString(): String = value.toString()
}

fun <T : Enum<*>> T.asTraceable(trace: Trace) = TraceableEnum(this, trace)
fun Path.asTraceable(trace: Trace) = TraceablePath(this, trace)
fun String.asTraceable(trace: Trace) = TraceableString(this, trace)
