/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.cli

import com.github.ajalt.clikt.core.TypoSuggestor

private val introspectionCommands = setOf("modules", "tasks", "settings")

/**
 * Creates a custom typo-suggester that handles special cases for em dashes, and falls back to the default similarity
 * metric otherwise.
 */
fun amperTypoSuggestor(defaultSuggestor: TypoSuggestor): TypoSuggestor {
    return { enteredValue, possibleValues ->
        if (enteredValue in introspectionCommands && "show" in possibleValues) {
            listOf("show $enteredValue") + defaultSuggestor(enteredValue, possibleValues)
        } else {
            defaultSuggestor(enteredValue, possibleValues)
        }
    }
}
