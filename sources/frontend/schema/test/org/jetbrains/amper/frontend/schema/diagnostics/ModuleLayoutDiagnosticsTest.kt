/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.schema.diagnostics

import org.jetbrains.amper.frontend.helpers.FrontendTestCaseBase
import org.jetbrains.amper.frontend.helpers.diagnosticsTest
import kotlin.io.path.Path
import kotlin.test.Test

class ModuleLayoutDiagnosticsTest: FrontendTestCaseBase(Path("testResources/diagnostics")) {

    @Test
    fun `unsupported layout when it's not jvm-app or jvm-lib`() {
        diagnosticsTest("unsupported-layout")
    }
}

