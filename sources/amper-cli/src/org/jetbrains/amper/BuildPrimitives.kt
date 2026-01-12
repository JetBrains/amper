/*
 * Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper

import kotlinx.coroutines.runInterruptible
import org.jetbrains.amper.telemetry.spanBuilder
import org.jetbrains.amper.telemetry.use
import java.nio.file.Path
import kotlin.io.path.copyToRecursively
import kotlin.io.path.pathString

object BuildPrimitives {
    // defaults are selected for build system-stuff
    suspend fun copy(from: Path, to: Path, overwrite: Boolean = false, followLinks: Boolean = true) {
        // Do not change coroutine context, we want to stay in tasks pool

        spanBuilder("copy")
            .setAttribute("from", from.pathString)
            .setAttribute("to", to.pathString)
            .use {
                // TODO is it really interruptible here?
                runInterruptible {
                    from.copyToRecursively(to, overwrite = overwrite, followLinks = followLinks)
                }
            }
    }
}