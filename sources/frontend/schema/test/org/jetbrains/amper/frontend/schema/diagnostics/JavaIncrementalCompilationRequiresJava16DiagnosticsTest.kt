/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.schema.diagnostics

import org.jetbrains.amper.frontend.helpers.FrontendTestCaseBase
import org.jetbrains.amper.frontend.helpers.diagnosticsTest
import kotlin.io.path.Path
import kotlin.test.Test

class JavaIncrementalCompilationRequiresJava16DiagnosticsTest : FrontendTestCaseBase(Path("testResources/diagnostics")) {

    @Test
    fun `jic requires java 16`() {
        diagnosticsTest("jic-requires-java16")
    }
}
