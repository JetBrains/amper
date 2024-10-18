/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.processes

interface ProcessOutputListener {
    fun onStdoutLine(line: String)
    fun onStderrLine(line: String)

    object NOOP: ProcessOutputListener {
        override fun onStdoutLine(line: String) = Unit
        override fun onStderrLine(line: String) = Unit
    }
}
