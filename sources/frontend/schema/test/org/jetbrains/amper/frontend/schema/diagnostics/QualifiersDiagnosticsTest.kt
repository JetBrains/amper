/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.schema.diagnostics

import org.jetbrains.amper.test.golden.GoldenTestBase
import org.jetbrains.amper.frontend.schema.helper.diagnosticsTest
import kotlin.io.path.Path
import kotlin.io.path.div
import kotlin.test.Test

class QualifiersDiagnosticsTest : GoldenTestBase(Path("testResources") / "diagnostics") {
    @Test
    fun `unknown qualifiers`() {
        diagnosticsTest("unknown-qualifiers")
    }

    @Test
    fun `multiple qualifiers are not supported yet`() {
        diagnosticsTest("multiple-qualifiers")
    }
}
