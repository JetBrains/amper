/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.jps

private const val NEW_LINE_REPLACEMENT = "\u0001"

object JicOutputAutoFlushWorkaround {

    fun serializeJpsCompilerOutput(output: String): String {
        return output.replace("\n", NEW_LINE_REPLACEMENT)
    }

    fun deserializeJpsCompilerOutput(output: String): String {
        return output.replace(NEW_LINE_REPLACEMENT, "\n")
    }

}