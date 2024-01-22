/*
 * Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.api

import kotlin.reflect.KProperty0

/**
 * An entity, that can persist its trace.
 */
abstract class Traceable {
    open var trace: Any? = null
}

/**
 * A string value, that can persist its trace.
 */
class TraceableString(
    val value: String
) : Traceable() {
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