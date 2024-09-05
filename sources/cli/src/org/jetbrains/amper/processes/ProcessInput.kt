/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.processes

import org.jetbrains.annotations.TestOnly

sealed interface ProcessInput {
    /**
     * Child process inherits the stdin from the current process.
     *
     * Warning: `System.setIn()` doesn't change the input to be inherited.
     */
    data object Inherit : ProcessInput

    /**
     * Provides a simple string as a single "write" operation for the process.
     * No EOF is guaranteed immediately after the input though.
     */
    data class SimpleInput @TestOnly constructor (val input: String) : ProcessInput
}
