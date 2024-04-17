/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.cli

import org.jetbrains.amper.test.generateUnifiedDiff
import kotlin.test.Test
import kotlin.test.assertEquals

class CommandTest {
    @Test
    fun cliCommandsAreSorted() {
        val names = RootCommand().registeredSubcommandNames()
        assertEquals(
            names.sorted(), names, "Amper subcommands in RootCommand are not sorted:\n" +
            generateUnifiedDiff(names, "current", names.sorted(), "sorted")
        )
    }
}
