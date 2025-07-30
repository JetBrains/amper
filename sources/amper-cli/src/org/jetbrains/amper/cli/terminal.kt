/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.cli

import com.github.ajalt.mordant.rendering.TextColors
import com.github.ajalt.mordant.rendering.TextStyle
import com.github.ajalt.mordant.rendering.Theme
import com.github.ajalt.mordant.terminal.Terminal
import org.jetbrains.amper.core.telemetry.spanBuilder
import org.jetbrains.amper.telemetry.useWithoutCoroutines

fun createMordantTerminal(): Terminal = spanBuilder("Initialize Mordant terminal").useWithoutCoroutines {
    Terminal(theme = createAmperTerminalTheme())
}

private fun createAmperTerminalTheme(): Theme = Theme {
    // The default is too low contrast and too flashy (highlight blue color on a medium gray background).
    // This has to read well on both dark and light background. See AMPER-4433 for experiments.
    styles["markdown.code.span"] = TextColors.rgb("#7fa77d")

    // The default is too flashy (highlight blue color).
    // Markdown blocks are already in a box, so they are visible enough - no need for extra style
    styles["markdown.code.block"] = TextStyle()
}
