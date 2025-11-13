/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.schema.diagnostics

import org.jetbrains.amper.frontend.helpers.FrontendTestCaseBase
import org.jetbrains.amper.frontend.helpers.diagnosticsTest
import kotlin.io.path.Path
import kotlin.test.Test

class JUnit6RequiresJava17DiagnosticsTest : FrontendTestCaseBase(Path("testResources/diagnostics")) {

    @Test
    fun `default requires java 17`() {
        diagnosticsTest("junit6-requires-java17-default-junit")
    }
    @Test
    fun `explicit junit 6 requires java 17`() {
        diagnosticsTest("junit6-requires-java17-custom-junit")
    }
}
