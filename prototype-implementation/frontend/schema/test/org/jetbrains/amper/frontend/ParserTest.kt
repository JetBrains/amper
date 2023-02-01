/*
 * Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend

import kotlin.test.Ignore
import kotlin.test.Test

class ParserTest {

    // TODO Check that there are all of settings withing that file.
    @Test
    fun `all module settings are parsed without errors`() =
        moduleParseTest("all-module-settings")

    @Test
    @Ignore
    fun `all template settings are parsed without errors`() {
        TODO()
    }

    @Test
    @Ignore
    fun `all module settings are parsed correctly`() {
        TODO()
    }

    @Test
    @Ignore
    fun `all template settings are parsed correctly`() {
        TODO()
    }

    @Test
    @Ignore
    fun `redundant module settings are causing errors`() {
        TODO()
    }

    @Test
    @Ignore
    fun `redundant template settings are causing errors`() {
        TODO()
    }

}