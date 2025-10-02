/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.schema.diagnostics

import org.jetbrains.amper.frontend.helpers.FrontendTestCaseBase
import org.jetbrains.amper.frontend.helpers.diagnosticsTest
import kotlin.io.path.Path
import kotlin.io.path.div
import kotlin.test.Test

class AndroidDiagnosticsTest : FrontendTestCaseBase(Path("testResources") / "diagnostics" / "android") {
    @Test
    fun `test Android too old version`() {
        diagnosticsTest("too-old-version")
    }

    @Test
    fun `test Android all versions are at least minSdk`() {
        diagnosticsTest("at-least-min-sdk")
    }

    @Test
    fun `test Android version is at least minSdk from different context`() {
        diagnosticsTest("at-least-min-sdk-from-context")
    }

    @Test
    fun `test setting cannot be applied to android library`() {
        diagnosticsTest("signing-in-libraries")
    }
}
