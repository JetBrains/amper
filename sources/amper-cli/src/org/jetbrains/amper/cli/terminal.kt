/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.cli

import com.github.ajalt.mordant.input.KeyboardEvent
import com.github.ajalt.mordant.input.interactiveMultiSelectList
import com.github.ajalt.mordant.input.interactiveSelectList
import com.github.ajalt.mordant.rendering.TextColors
import com.github.ajalt.mordant.rendering.TextStyle
import com.github.ajalt.mordant.rendering.Theme
import com.github.ajalt.mordant.terminal.Terminal
import com.github.ajalt.mordant.widgets.SelectList
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

/**
 * Displays a list of items and allows the user to select one with the arrow keys and enter.
 */
internal fun <T : Any> Terminal.interactiveSelectList(
    items: List<T>,
    nameSelector: (T) -> String,
    descriptionSelector: ((T) -> String)? = null,
    title: String = "",
    filterable: Boolean = false,
): T? {
    val itemsByName = items.associateBy(nameSelector)
    val choice = interactiveSelectList {
        title(title)
        if (descriptionSelector != null) {
            entries(items.map { SelectList.Entry(nameSelector(it), descriptionSelector(it)) })
        } else {
            entries(itemsByName.keys)
        }
        filterable(filterable)
    } ?: return null
    return itemsByName[choice] ?: error("Item with name '$choice' not found")
}

/**
 * Display a list of items and allow the user to select zero or more with the arrow keys and enter.
 *
 * @return the selected items, or null if the user canceled the selection
 */
internal fun <T : Any> Terminal.interactiveMultiSelectList(
    items: List<T>,
    nameSelector: (T) -> String,
    descriptionSelector: ((T) -> String)? = null,
    title: String = "",
    filterable: Boolean = false,
): List<T>? {
    val itemsByName = items.associateBy(nameSelector)
    val choices = interactiveMultiSelectList {
        title(title)
        if (descriptionSelector != null) {
            entries(items.map { SelectList.Entry(nameSelector(it), descriptionSelector(it)) })
        } else {
            entries(itemsByName.keys)
        }
        filterable(filterable)
    } ?: return null
    return choices.map { itemsByName[it] ?: error("Item with name '$it' not found") }
}
