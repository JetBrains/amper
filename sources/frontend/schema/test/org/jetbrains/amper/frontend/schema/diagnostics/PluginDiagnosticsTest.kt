/*
 * Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.schema.diagnostics

import org.jetbrains.amper.frontend.helpers.FrontendTestCaseBase
import org.jetbrains.amper.frontend.helpers.diagnosticsTest
import kotlin.io.path.Path
import kotlin.io.path.div
import kotlin.test.Test

class PluginDiagnosticsTest : FrontendTestCaseBase(Path("testResources") / "diagnostics" / "plugins") {

    @Test
    fun `plugin with deprecated description`() {
        diagnosticsTest("plugin-with-deprecated-description")
    }
}
