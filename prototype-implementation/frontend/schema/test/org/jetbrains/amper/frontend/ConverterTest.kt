/*
 * Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend

import kotlin.test.Ignore
import kotlin.test.Test

class ConverterTest {

    // TODO Check that there are all of settings withing that file.
    @Test
    fun `all module settings are converted without errors`() =
        moduleConvertTest("all-module-settings")

    @Test
    @Ignore
    fun `all template settings are converted without errors`() {
        TODO()
    }

    @Test
    @Ignore
    fun `all module settings are converted correctly`() {
        TODO()
    }

    @Test
    @Ignore
    fun `all template settings are converted correctly`() {
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