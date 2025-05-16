/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.cli

import com.github.ajalt.clikt.core.TypoSuggestor

private val introspectionCommands = setOf("modules", "tasks", "settings")
private val winLikePlatforms = setOf("win", "windows", "win32", "win64")

/**
 * Creates a custom typo-suggester with additional special cases for show commands, and falls back to the default
 * similarity metric otherwise.
 */
fun TypoSuggestor.withShowCommandSuggestions(): TypoSuggestor = withExtraSuggestions { enteredValue, possibleValues ->
    if (enteredValue in introspectionCommands && "show" in possibleValues) {
        listOf("show $enteredValue")
    } else {
        emptyList()
    }
}

/**
 * Adds extra suggestions for invalid platforms.
 */
fun TypoSuggestor.withPlatformSuggestions(): TypoSuggestor = withExtraSuggestions { enteredValue, possibleValues ->
    if (enteredValue in winLikePlatforms) {
        possibleValues.filter { "mingw" in it }
    } else {
        emptyList()
    }
}

/**
 * Prepend extra suggestions to the default suggestions from this [TypoSuggestor].
 */
private fun TypoSuggestor.withExtraSuggestions(extraSuggestions: TypoSuggestor): TypoSuggestor {
    val current = this
    return { enteredValue, possibleValues ->
        extraSuggestions(enteredValue, possibleValues) + current(enteredValue, possibleValues)
    }
}
