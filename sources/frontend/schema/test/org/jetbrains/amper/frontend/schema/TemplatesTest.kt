/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.schema

import org.jetbrains.amper.frontend.helpers.FrontendTestCaseBase
import org.jetbrains.amper.frontend.helpers.aomTest
import kotlin.io.path.Path
import kotlin.io.path.div
import kotlin.test.Test

internal class TemplatesTest : FrontendTestCaseBase(Path("testResources") / "templates") {

    @Test
    fun `check artifacts of multi-variant builds`() {
        aomTest("templates-simple")
    }

    @Test
    fun `check path literals are adjusted`() {
        aomTest(
            "templates-adjust-path-test",
            // TODO: Rewrite this test to properly reflect the project structure
            expectedError = "Cannot find a module file './some-dep-2'",
        )
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
