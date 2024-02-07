/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.intellij

import com.intellij.execution.CommandLineUtil

object CommandLineUtils {
    fun quoteCommandLineForCurrentPlatform(cmd: List<String>): List<String> =
        CommandLineUtil.toCommandLine(cmd)
}
