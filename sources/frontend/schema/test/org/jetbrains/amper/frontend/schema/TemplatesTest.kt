/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.schema

import org.jetbrains.amper.frontend.schema.helper.aomTest
import org.jetbrains.amper.test.golden.GoldenTestBase
import kotlin.io.path.Path
import kotlin.io.path.div
import kotlin.test.Test

internal class TemplatesTest : GoldenTestBase(Path("testResources") / "templates") {

    @Test
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
