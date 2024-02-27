/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.cli

import org.jetbrains.amper.intellij.IntelliJPlatformInitializer

object CliEnvironmentInitializer {
    private val init by lazy {
        IntelliJPlatformInitializer.setup()

        // see https://github.com/Anamorphosee/stacktrace-decoroutinator#motivation
        // Temporary disabled due to unresolved issues with it AMPER-396 CLI: Provide coroutine stacktraces
        // DecoroutinatorRuntime.load()
    }

    fun setup() = init
}
