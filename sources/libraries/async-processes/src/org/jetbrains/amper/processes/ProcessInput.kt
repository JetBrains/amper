/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.processes

import java.io.OutputStream

sealed interface ProcessInput {

    /**
     * The child process inherits the standard input from the current process.
     *
     * Warning: `System.setIn()` doesn't change the input to be inherited.
     */
    data object Inherit : ProcessInput {
        override fun writeTo(processStdin: OutputStream) = Unit
    }

    /**
     * Nothing is sent to the standard input of the child process, the stream is immediately closed.
     */
    data object Empty : ProcessInput {
        override fun writeTo(processStdin: OutputStream) = Unit
    }

    /**
     * The given [input] text is written as UTF-8 in a single write operation to the child process' standard input.
     * No EOF is guaranteed immediately after the input though.
     */
    data class Text(val input: String) : ProcessInput {
        override fun writeTo(processStdin: OutputStream) = processStdin.write(input.encodeToByteArray())
    }

    fun writeTo(processStdin: OutputStream)
}
