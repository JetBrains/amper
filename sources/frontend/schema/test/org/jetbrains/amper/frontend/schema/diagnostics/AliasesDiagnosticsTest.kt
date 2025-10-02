/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.schema.diagnostics

import org.jetbrains.amper.frontend.helpers.FrontendTestCaseBase
import org.jetbrains.amper.frontend.helpers.diagnosticsTest
import kotlin.io.path.Path
import kotlin.io.path.div
import kotlin.test.Test

class AliasesDiagnosticsTest : FrontendTestCaseBase(Path("testResources") / "diagnostics" / "aliases") {
    @Test
    fun `aliases are not supported in single-platform modules`() {
        diagnosticsTest("alias-in-single-platform-module")
    }

    @Test
    fun `empty alias`() {
        diagnosticsTest("empty-alias")
    }

    @Test
    fun `alias intersects with natural hierarchy`() {
        diagnosticsTest("alias-intersects-with-natural-hierarchy")
    }

    @Test
    fun `alias uses undeclared platform`() {
        diagnosticsTest("alias-uses-undeclared-platform")
    }

    @Test
    fun `alias uses non-leaf platform`() {
        diagnosticsTest("alias-uses-non-leaf-platform")
    }

    @Test
    fun `alias non-leaf platform expands to nothing`() {
        diagnosticsTest("alias-non-leaf-platform-expands-to-nothing")
    }
}