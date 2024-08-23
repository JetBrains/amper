/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.api

import com.intellij.psi.PsiElement
import org.jetbrains.amper.frontend.VersionCatalog
import java.nio.file.Path
import kotlin.reflect.KProperty0

/**
 * A trace is a backreference that allows to determine the source of the model property/value.
 * It can be a PSI element, or a synthetic trace in case the property has been constructed programmatically.
 */
sealed interface Trace {
    val precedingValue: ValueBase<*>?
}

/**
 * Property with this trace originates from the node of a PSI tree, in most cases from the manifest file.
 */
class PsiTrace(val psiElement: PsiElement, override val precedingValue: ValueBase<*>? = null) : Trace

/**
 * Property with this trace originates from the version catalog provided by the toolchain.
 */
class BuiltinCatalogTrace(val catalog: VersionCatalog) : Trace {
    override val precedingValue: ValueBase<*>? = null
}

fun <V> Trace.withPrecedingValue(precedingValue: ValueBase<V>?): Trace =
    if (precedingValue != null && this is PsiTrace) PsiTrace(this.psiElement, precedingValue)
    else this

/**
 * An entity that can persist its trace.
 */
interface Traceable {
    var trace: Trace?
}

/**
 * A string value that can persist its trace.
 */
class TraceableString(
    val value: String
) : Traceable {
    override var trace: Trace? = null

    override fun toString(): String {
        return value
    }

    override fun hashCode() = value.hashCode()
    override fun equals(other: Any?) =
        this === other || (other as? TraceableString)?.value == value
}

class TraceablePath(
    val value: Path
) : Traceable {
    override var trace: Trace? = null

    override fun toString(): String {
        return value.toString()
    }

    override fun hashCode() = value.hashCode()
    override fun equals(other: Any?) =
        this === other || (other as? TraceablePath)?.value == value
}

// TODO Replace by traceability generalization.
fun KProperty0<String>.toTraceableString(): TraceableString = TraceableString(get()).withTraceFrom(this.valueBase)

fun <T : Traceable> T.withTraceFrom(other: Traceable?): T = apply { trace = other?.trace }

/**
 * Adds a trace to this [Traceable] pointing to the given [element], and returns this [Traceable] for chaining.
 * If [element] is null, the trace is set to null.
 */
fun <T : Traceable> T.applyPsiTrace(element: PsiElement?) = apply { trace = element?.let(::PsiTrace) }
