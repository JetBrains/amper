/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.schema.diagnostics

import org.jetbrains.amper.frontend.helpers.FrontendTestCaseBase
import org.jetbrains.amper.frontend.helpers.diagnosticsTest
import org.junit.jupiter.api.Test
import kotlin.io.path.Path
import kotlin.io.path.div

class TemplateDiagnosticsTest : FrontendTestCaseBase(Path("testResources") / "diagnostics" / "templates") {
    @Test
    fun `unresolved template`() {
        diagnosticsTest("unresolved-template")
    }

    @Test
    fun `template with incorrect suffix`() {
        diagnosticsTest("template-with-incorrect-suffix")
    }
}