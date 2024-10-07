/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.schemaConverter.psi

class Pointer(val segmentName: String? = null,
              var prev: Pointer? = null,
              var next: Pointer? = null) {
    private val firstSegment get() = run {
        var p: Pointer = this
        while (p.prev != null) p = p.prev!!
        p
    }

    operator fun plus(value: String): Pointer {
        if (segmentName == null && prev == null && next == null) {
            return Pointer(value)
        }

        val copy = deepCopyWithPrev()
        val newPointer = Pointer(value, copy)
        copy.next = newPointer
        return newPointer
    }

    private fun deepCopyWithPrev(): Pointer {
        val prevCopy = prev?.deepCopyWithPrev()
        val copy = Pointer(segmentName, prevCopy)
        prevCopy?.next = copy
        return copy
    }

    fun nextAfter(o: Pointer): Pointer? {
        var own: Pointer? = firstSegment
        var other: Pointer? = o.firstSegment
        while (other != null) {
            if (own == null || own.segmentName != other.segmentName) return null
            if (own == this || other == o) break
            own = own.next
            other = other.next
        }
        return own?.next
    }

    fun startsWith(o: Pointer): Boolean {
        var own: Pointer? = firstSegment
        var other: Pointer? = o.firstSegment
        while (other != null) {
            if (own == null || own.segmentName != other.segmentName) return false
            if (other == o) break
            if (own == this) {
                return other.next == null
            }
            own = own.next
            other = other.next
        }
        return true
    }

    override fun hashCode(): Int {
        return toString().hashCode()
    }

    override fun equals(other: Any?): Boolean {
        if (other !is Pointer || segmentName != other.segmentName) return false
        return toString() == other.toString()
    }

    override fun toString(): String {
        val builder = StringBuilder()
        val firstSegment1 = firstSegment
        var p: Pointer? = firstSegment1
        while (p != null) {
            if (p !== firstSegment1) builder.append(" :: ")
            builder.append(p.segmentName)
            if (p === this) break
            p = p.next
        }
        return builder.toString()
    }
}