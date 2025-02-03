/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.schema.diagnostics

import org.jetbrains.amper.frontend.old.helper.TestBase
import org.jetbrains.amper.frontend.schema.helper.diagnosticsTest
import kotlin.io.path.Path
import kotlin.io.path.div
import kotlin.test.Test

class AliasesDiagnosticsTest : TestBase(Path("testResources") / "diagnostics") {

    @Test
    fun `alias intersects with natural hierarchy`() {
        diagnosticsTest("alias-intersects-with-natural-hierarchy")
    }

    @Test
    fun `alias uses undeclared platform`() {
        diagnosticsTest("alias-uses-undeclared-platform")
    }
}