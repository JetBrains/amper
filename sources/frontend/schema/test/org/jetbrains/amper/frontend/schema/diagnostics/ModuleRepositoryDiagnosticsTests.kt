/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.schema.diagnostics

import org.jetbrains.amper.frontend.schema.helper.diagnosticsTest
import org.jetbrains.amper.problems.reporting.Level
import org.jetbrains.amper.test.golden.GoldenTestBase
import kotlin.io.path.Path
import kotlin.test.Test

class ModuleRepositoryDiagnosticsTests : GoldenTestBase(Path("testResources/diagnostics/repositories")) {

    @Test
    fun `no warning for maven local repository`() {
        diagnosticsTest("repositories-mavenlocal", levels = arrayOf(Level.Warning))
    }
}
