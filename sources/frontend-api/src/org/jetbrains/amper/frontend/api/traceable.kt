/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.api

import com.intellij.psi.PsiElement
import com.intellij.util.asSafely
import org.jetbrains.amper.frontend.VersionCatalog
import org.jetbrains.amper.frontend.tree.TreeValue
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
    val precedingValue: TreeValue<*>? // old value suppressed by merge or inheritance

    /**
     * If the value of a property is computed based on the value of another property, then trace to that value is registered here.
     */
    val computedValueTrace: Traceable?
}

open class DefaultTrace(
    override val computedValueTrace: Traceable?,
) : Trace {
    companion object : DefaultTrace(null)
    override val precedingValue: TreeValue<*>? = null
}

val Trace.precedingValuesSequence get() =
    generateSequence(precedingValue) { it.trace.precedingValue }

/**
 * The Property with this trace originates from the node of a PSI tree, in most cases from the manifest file.
 */
data class PsiTrace(
    val psiElement: PsiElement,
    override val precedingValue: TreeValue<*>? = null,
    override val computedValueTrace: Traceable? = null,
) : Trace

fun PsiElement.asTrace() = PsiTrace(this)
val PsiElement.trace get() = PsiTrace(this)

/**
 * The Property with this trace originates from the version catalog provided by the toolchain.
 */
data class BuiltinCatalogTrace(
    val catalog: VersionCatalog,
    override val computedValueTrace: Traceable? = null,
) : Trace {
    override val precedingValue: TreeValue<*>? = null
}

fun Trace.withPrecedingValue(precedingValue: TreeValue<*>): Trace = when (this) {
    is PsiTrace -> copy(precedingValue = precedingValue)
    else -> this
}

fun Trace.withComputedValueTrace(computedValueTrace: Traceable?): Trace = when {
    computedValueTrace == null -> this
    this is PsiTrace -> copy(computedValueTrace = computedValueTrace)
    this is BuiltinCatalogTrace -> copy(computedValueTrace = computedValueTrace)
    this is DefaultTrace -> DefaultTrace(computedValueTrace)
    else -> this
}

/**
 * An entity that can persist its trace.
 */
interface Traceable {
    // todo (AB): nullable trace inside Traceable interface doesn't make any sense from the model consumer point of view.
    // todo (AB): it seems this is done mostly because of the trace is initialized (lately), maybe lateinit could be a solution here?
    @property:IgnoreForSchema
    var trace: Trace?
}

/**
 * Basic wrapper for classes that have different trace contract (basically, non nullable).
 */
open class TrivialTraceable(override var trace: Trace? = null) : Traceable

/**
 * A value that can persist its trace.
 */
abstract class TraceableValue<T : Any>(val value: T) : TrivialTraceable() {
    override fun toString() = value.toString()
    override fun hashCode() = value.hashCode()
    override fun equals(other: Any?) = this === other || other?.asSafely<TraceableValue<*>>()?.value == value
}

open class TraceableString(value: String) : TraceableValue<String>(value)

class TraceableVersion(value: String, source: ValueDelegateBase<*>?) : TraceableString(value) {
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
