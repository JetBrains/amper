/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.schema.diagnostics

import org.jetbrains.amper.frontend.helpers.FrontendTestCaseBase
import org.jetbrains.amper.frontend.helpers.diagnosticsTest
import kotlin.io.path.Path
import kotlin.test.Test

class ModuleRepositoryDiagnosticsTests : FrontendTestCaseBase(Path("testResources/diagnostics/repositories")) {

    @Test
    fun `no warning for maven local repository`() {
        diagnosticsTest("repositories-mavenlocal")
    }
}
