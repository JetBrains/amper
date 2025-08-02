/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.diagnostics

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.debug.DebugProbes
import org.jetbrains.amper.core.telemetry.spanBuilder
import org.jetbrains.amper.telemetry.useWithoutCoroutines
import org.slf4j.LoggerFactory

object CoroutinesDebug {

    private val logger = LoggerFactory.getLogger(CoroutinesDebug::class.java)

    @OptIn(ExperimentalCoroutinesApi::class)
    fun setupCoroutinesInstrumentation() {
        spanBuilder("Install coroutines debug probes").useWithoutCoroutines {
            // coroutines debug probes, required to dump coroutines
            DebugProbes.enableCreationStackTraces = false
            try {
                DebugProbes.install()
            } catch (e: Throwable) {
                // Always fails on Windows Arm64 because ByteBuddy doesn't support it:
                // https://github.com/raphw/byte-buddy/issues/1336
                logger.warn("Failed to install coroutines debug probes: $e")
            }
        }
    }
}
