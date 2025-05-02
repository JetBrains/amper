/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.schema.diagnostics

import org.jetbrains.amper.core.messages.Level
import org.jetbrains.amper.frontend.old.helper.TestBase
import org.jetbrains.amper.frontend.schema.helper.diagnosticsTest
import kotlin.io.path.Path
import kotlin.io.path.div
import kotlin.test.Test

class ModuleRepositoryDiagnosticsTests : TestBase(Path("testResources/diagnostics/repositories")) {

    @Test
    fun `implicitly resolvable maven local repository`() {
        diagnosticsTest("repositories-mavenlocal-resolve-implicit", levels = arrayOf(Level.Warning))
    }

    @Test
    fun `explicitly resolvable maven local repository`() {
        diagnosticsTest("repositories-mavenlocal-resolve-explicit", levels = arrayOf(Level.Warning))
    }

    @Test
    fun `no warning when maven local repository is correctly set with resolve false`() {
        diagnosticsTest("repositories-mavenlocal-resolve-false", levels = arrayOf(Level.Warning))
    }
}
