/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.diagnostics

import dev.reformator.stacktracedecoroutinator.jvm.DecoroutinatorJvmApi
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.debug.DebugProbes
import org.jetbrains.amper.core.telemetry.spanBuilder
import org.jetbrains.amper.telemetry.useWithoutCoroutines

object CoroutinesDebug {

    @OptIn(ExperimentalCoroutinesApi::class)
    fun setupCoroutinesInstrumentation() {
        // TODO investigate the performance impact of the decoroutinator
        spanBuilder("Install stacktrace-decoroutinator").useWithoutCoroutines {
            // see https://github.com/Anamorphosee/stacktrace-decoroutinator#motivation
            DecoroutinatorJvmApi.install()
        }

        spanBuilder("Install coroutines debug probes").useWithoutCoroutines {
            // coroutines debug probes, required to dump coroutines
            DebugProbes.enableCreationStackTraces = false
            DebugProbes.install()
        }
    }
}
