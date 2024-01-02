/*
 * Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.api

/**
 * An entity, that can persist its trace.
 */
abstract class Traceable {
    var trace: Any? = null
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
fun SchemaValue<String>.toTraceableString(): TraceableString = TraceableString(value).apply {
    trace = this@toTraceableString.trace
}