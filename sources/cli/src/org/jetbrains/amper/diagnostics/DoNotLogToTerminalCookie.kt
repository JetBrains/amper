/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.diagnostics

import org.slf4j.MDC

/**
 * Under this block, all logging will go only to files, not to the user terminal
 */
object DoNotLogToTerminalCookie {
    fun <R> use(block: () -> R): R = MDC.putCloseable(REPEL_TERMINAL_LOGGING_MDC_NAME, "1").use {
        block()
    }

    internal const val REPEL_TERMINAL_LOGGING_MDC_NAME = "REPEL_TERMINAL_LOGGING_MDC_NAME"
}