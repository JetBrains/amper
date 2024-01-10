/*
 * Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.schema

import org.jetbrains.amper.frontend.old.helper.TestBase
import org.jetbrains.amper.frontend.schema.helper.aomTest
import kotlin.io.path.Path
import kotlin.io.path.div
import kotlin.test.Ignore
import kotlin.test.Test

internal class TemplatesTest : TestBase(Path("testResources") / "templates") {

    // TODO Fix
    @Test
    @Ignore
    fun `check artifacts of multi-variant builds`() {
        aomTest("templates-simple")
    }

    @Test
    fun `check path literals are adjusted`() {
        aomTest("templates-adjust-path-test")
    }

    @Test
    fun `empty template file`() {
        aomTest("templates-empty-file")
    }

    @Test
    fun `empty apply list file`() {
        aomTest("templates-empty-apply")
    }
}
