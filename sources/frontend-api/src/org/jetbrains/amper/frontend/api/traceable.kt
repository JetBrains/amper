/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.api

import com.intellij.psi.PsiElement
import com.intellij.util.asSafely
import org.jetbrains.amper.frontend.VersionCatalog
import java.nio.file.Path

/**
 * A trace is a backreference that allows to determine the source of the model property/value.
 * It can be a PSI element, or a synthetic trace in case the property has been constructed programmatically.
 */
sealed interface Trace {
    /**
     * Old value defined initially and suppressed by merge or inheritance during model building,
     * it could be a chain of the subsequent preceding values built during merge of templates and module file definitions.
     */
    val precedingValue: ValueBase<*>? // old value suppressed by merge or inheritance

    /**
     * If the value of a property is computed based on the value of another property, then trace to that value is registered here.
     */
    val computedValueTrace: Traceable?
}


/**
 * This is for cases when there is no PSI element for the value being calculated.
 */
data class DefaultValueDependentTrace(
    override val computedValueTrace: Traceable? = null,
    override val precedingValue: ValueBase<*>? = null
) : Trace

/**
 * The Property with this trace originates from the node of a PSI tree, in most cases from the manifest file.
 */
data class PsiTrace(
    val psiElement: PsiElement,
    override val precedingValue: ValueBase<*>? = null,
    override val computedValueTrace: Traceable? = null,
) : Trace

/**
 * The Property with this trace originates from the version catalog provided by the toolchain.
 */
data class BuiltinCatalogTrace(
    val catalog: VersionCatalog,
    override val computedValueTrace: Traceable? = null,
) : Trace {
    override val precedingValue: ValueBase<*>? = null
}

fun <V> Trace.withPrecedingValue(precedingValue: ValueBase<V>?): Trace =
    when {
        precedingValue == null -> this
        this is PsiTrace -> this.copy(precedingValue = precedingValue)
        this is DefaultValueDependentTrace -> this.copy(precedingValue = precedingValue)
        this is BuiltinCatalogTrace -> this
        else -> this
    }

fun Trace.withComputedValueTrace(computedValueTrace: Traceable?): Trace =
    if (computedValueTrace == null)
        this
    else {
        when (this) {
            is PsiTrace -> this.copy(computedValueTrace = computedValueTrace)
            is DefaultValueDependentTrace -> this.copy(computedValueTrace = computedValueTrace)
            is BuiltinCatalogTrace -> this.copy(computedValueTrace = computedValueTrace)
        }
    }

fun Traceable.withComputedValueTrace(substitutionTrace: Traceable?) {
    trace = trace?.withComputedValueTrace(substitutionTrace)
}

/**
 * An entity that can persist its trace.
 */
interface Traceable {
    // todo (AB): nullable trace inside Traceable interface doesn't make any sense from the model consumer point of view.
    // todo (AB): it seems this is done mostly because of the trace is initialized (lately), maybe lateinit could be a solution here?
    var trace: Trace?
}

/**
 * A value that can persist its trace.
 */
abstract class TraceableValue<T : Any>(val value: T) : Traceable {
    override var trace: Trace? = null
    override fun toString() = value.toString()
    override fun hashCode() = value.hashCode()
    override fun equals(other: Any?) = this === other || other?.asSafely<TraceableValue<*>>()?.value == value
}

open class TraceableString(value: String) : TraceableValue<String>(value)

class TraceableVersion(value: String, source: ValueBase<*>?) : TraceableString(value) {
    init {
        trace = source?.trace
    }
}

class TraceablePath(value: Path) : TraceableValue<Path>(value)

fun <T : Traceable> T.withTraceFrom(other: Traceable?): T = apply { trace = other?.trace }

/**
 * Adds a trace to this [Traceable] pointing to the given [element], and returns this [Traceable] for chaining.
 * If [element] is null, the trace is set to null.
 */
// todo (AB) : Remove nullability here.
fun <T : Traceable> T.applyPsiTrace(element: PsiElement?) = apply { trace = element?.let(::PsiTrace) }
