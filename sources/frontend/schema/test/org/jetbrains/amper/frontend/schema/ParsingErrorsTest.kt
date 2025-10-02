/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.schema

import org.jetbrains.amper.frontend.helpers.FrontendTestCaseBase
import org.jetbrains.amper.frontend.helpers.diagnosticsTest
import kotlin.io.path.Path
import kotlin.io.path.div
import kotlin.test.Test

class ParsingErrorsTest : FrontendTestCaseBase(Path("testResources") / "parsing-errors") {
    @Test
    fun `unexpected value type`() {
        diagnosticsTest("unexpected-value-type/module")
    }

    @Test
    fun `invalid dependencies`() {
        diagnosticsTest("invalid-dependencies/module")
    }

    @Test
    fun `unsupported constructs`() {
        diagnosticsTest("unsupported-constructs/module")
    }

    @Test
    fun `unsupported references`() {
        diagnosticsTest("unsupported-references/module")
    }
}