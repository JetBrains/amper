/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.api

import com.intellij.openapi.util.Key
import com.intellij.psi.PsiElement
import com.intellij.util.asSafely
import org.jetbrains.amper.frontend.types.kClassOrNull
import java.nio.file.Path
import kotlin.properties.PropertyDelegateProvider
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.KProperty
import kotlin.reflect.KProperty0
import kotlin.reflect.KProperty1
import kotlin.reflect.KType
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.hasAnnotation
import kotlin.reflect.full.starProjectedType
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


enum class ValueState {
    /**
     * The value has not been set yet.
     */
    UNSET,

    /**
     * The value has been set explicitly.
     */
    EXPLICIT,

    /**
     * The value has been inherited without merging.
     */
    INHERITED,

    /**
     * The value has been set explicitly and merged with previous one.
     */
    MERGED,
}

typealias ValueHolders = MutableMap<String, ValueHolder<*>>

data class ValueHolder<T>(
    val value: T,
    val state: ValueState,
    val trace: Trace? = null,
)

/**
 * Class to collect all values registered within it.
 */
abstract class SchemaNode : Traceable {
    internal val allValues = mutableListOf<ValueDelegateBase<*>>()
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

    override var trace: Trace? = null
        set(value) {
            if (value is PsiTrace) value.psiElement.putUserData(linkedAmperNode, this)
            field = value
        }
}

sealed class Default<out T> {
    abstract val value: T?

    data class Static<T>(override val value: T) : Default<T>()

    data class Lambda<T>(val desc: String?, private val getter: () -> T?) : Default<T>() {
        override val value by lazy { getter() }
        val isCtor by lazy {
            val getterAsKFunc = getter.asSafely<KFunction<*>>()
            getterAsKFunc?.returnType?.kClassOrNull?.constructors?.contains(getterAsKFunc) ?: false
        }
    }

    @Suppress("UNCHECKED_CAST")
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

fun PropertyMeta(name: String, klass: KClass<*>, default: Default<*>? = null) =
    PropertyMeta(name, klass.starProjectedType, default)

/**
 * Meta-information about schema property.
 */
open class PropertyMeta(
    val name: String,
    val type: KType,
    val default: Default<*>? = null,
    // TODO Rename to `isCtorArg`.
    val isCtorArg: Boolean = false,
    val hasShorthand: Boolean = false,
    val aliases: List<String>? = null,
    val knownStringValues: List<String>? = null,
    val platformSpecific: PlatformSpecific? = null,
    val platformAgnostic: PlatformAgnostic? = null,
    val productTypeSpecific: ProductTypeSpecific? = null,
    val gradleSpecific: GradleSpecific? = null,
    val standaloneSpecific: StandaloneSpecific? = null,
    val modifierAware: ModifierAware? = null,
    val schemaDoc: SchemaDoc? = null,
    val kProperty: KProperty<*>? = null,
) {
    val isNullable = type.isMarkedNullable
    val hasDefault = default != null
    val isValueRequired = !isNullable && !hasDefault
    val nameAndAliases = aliases.orEmpty() + name

    constructor(property: KProperty<*>, defaultHolder: Default<*>?) : this(
        property.name,
        property.returnType,
        defaultHolder,
        // FIXME Maybe introduce new annotation with meaningful name, or change this one.
        property.hasAnnotation<DependencyKey>(),
        property.hasAnnotation<Shorthand>(),
        property.findAnnotation<Aliases>()?.values?.distinct(),
        property.findAnnotation<KnownStringValues>()?.values?.toList(),
        property.findAnnotation<PlatformSpecific>(),
        property.findAnnotation<PlatformAgnostic>(),
        property.findAnnotation<ProductTypeSpecific>(),
        property.findAnnotation<GradleSpecific>(),
        property.findAnnotation<StandaloneSpecific>(),
        property.findAnnotation<ModifierAware>(),
        property.findAnnotation<SchemaDoc>(),
        property,
    )
}

/**
 * Abstract value that can have a default value.
 */
sealed class ValueDelegateBase<T>(
    val property: KProperty<*>,
    default: Default<T>?,
    valueHolders: ValueHolders,
) : PropertyMeta(property, default), Traceable, ReadWriteProperty<SchemaNode, T> {
    // We are creating lambdas here to prevent misusage of [valueHolders] from [ValueDelegateBase].
    private val valueGetter: () -> ValueHolder<T>? = { valueHolders[name] as ValueHolder<T>? }
    private val valueSetter: (ValueHolder<T>?) -> Unit = { if (it != null) valueHolders[name] = it }

    abstract val value: T
    val unsafe: T? get() = valueGetter()?.value ?: (default?.value as? T)
    val withoutDefault: T? get() = valueGetter()?.value
    val state get() = valueGetter()?.state ?: ValueState.UNSET

    /**
     * Overwrite current value if provided value is not null.
     */
    operator fun invoke(newValue: T?, newState: ValueState, newTrace: Trace? = null) =
        newValue?.let { valueSetter(ValueHolder(it, newState, newTrace ?: newValue.asSafely<Traceable>()?.trace)) }

    override fun getValue(thisRef: SchemaNode, property: KProperty<*>) = value
    override fun setValue(thisRef: SchemaNode, property: KProperty<*>, value: T) =
        invoke(value, ValueState.EXPLICIT) ?: Unit

    override var trace: Trace?
        get() = valueGetter()?.trace
            ?: default.asSafely<Default.Dependent<*, *>>()?.property?.setAccessible()?.valueBase?.let(::DefaultTrace)
        set(value) = run { valueGetter()?.copy(trace = value)?.let(valueSetter) }
}

private fun <T : KProperty<*>> T.setAccessible() = apply { isAccessible = true }

@Suppress("UNCHECKED_CAST")
fun <T, V> KProperty1<T, V>.valueBase(receiver: T): ValueDelegateBase<V>? =
    setAccessible().getDelegate(receiver) as? ValueDelegateBase<V>

@Suppress("UNCHECKED_CAST")
val <T> KProperty0<T>.valueBase: ValueDelegateBase<T>?
    get() = setAccessible().getDelegate() as? ValueDelegateBase<T>

val <T> KProperty0<T>.withoutDefault: T? get() = valueBase?.let { return it.withoutDefault } ?: get()

val <T> KProperty0<T>.isDefault get() = valueBase?.trace is DefaultTrace

val <T> KProperty0<T>.unsafe: T? get() = valueBase?.let { return it.unsafe } ?: get()

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
        it.allValues.sortedBy { it.name }.forEach { visit(it) }
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
class TraceableEnum<T : Enum<*>>(value: T) : TraceableValue<T>(value) {
    override var trace: Trace? = null
        set(value) {
            if (value is PsiTrace) value.psiElement.putUserData(linkedAmperEnumValue, this.value)
            field = value
        }

    override fun toString(): String = value.toString()
}

fun <T : Enum<*>> T.asTraceable(trace: Trace? = null) = TraceableEnum(this).apply { this.trace = trace }
fun Path.asTraceable(trace: Trace? = null) = TraceablePath(this).apply { this.trace = trace }
fun String.asTraceable(trace: Trace? = null) = TraceableString(this).apply { this.trace = trace }
