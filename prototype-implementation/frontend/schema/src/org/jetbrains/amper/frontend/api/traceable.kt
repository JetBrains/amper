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

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is TraceableString) return false

        if (value != other.value) return false

        return true
    }

    override fun hashCode(): Int {
        return value.hashCode()
    }
}