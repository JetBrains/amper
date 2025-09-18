/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.util

import io.opentelemetry.api.GlobalOpenTelemetry
import org.jetbrains.amper.cli.AmperBuildOutputRoot
import org.jetbrains.amper.cli.AmperVersion
import org.jetbrains.amper.incrementalcache.IncrementalCache

fun ExecuteOnChangedInputs(buildOutputRoot: AmperBuildOutputRoot): IncrementalCache =
    IncrementalCache(
        stateRoot = buildOutputRoot.path.resolve("incremental.state"),
        codeVersion = AmperVersion.codeIdentifier,
        openTelemetry = GlobalOpenTelemetry.get(),
    )
