/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.api

import com.intellij.psi.PsiElement
import org.jetbrains.amper.frontend.VersionCatalog
import kotlin.reflect.KProperty0

/**
 * A trace is a backreference that allows to determine the source of the model property/value.
 * It can be a PSI element, or a synthetic trace in case the property has been constructed programmatically.
 */
sealed interface Trace

/**
 * Property with this trace originates from the node of a PSI tree, in most cases from the manifest file.
 */
class PsiTrace(val psiElement: PsiElement) : Trace

/**
 * Property with this trace originates from the version catalog provided by the toolchain.
 */
class BuiltinCatalogTrace(val catalog: VersionCatalog) : Trace

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

// TODO Replace by traceability generalization.
fun KProperty0<String>.toTraceableString(): TraceableString = TraceableString(get()).apply {
    trace = this@toTraceableString.valueBase?.trace
}