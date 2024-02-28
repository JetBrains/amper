/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.schema.diagnostics

import org.jetbrains.amper.frontend.old.helper.TestBase
import org.jetbrains.amper.frontend.schema.helper.diagnosticsTest
import org.junit.jupiter.api.Test
import kotlin.io.path.Path
import kotlin.io.path.div

class JavaDiagnosticsTest : TestBase(Path("testResources") / "diagnostics" / "java")  {
    @Test
    fun `no java source`() {
        diagnosticsTest("no-java-source")
    }

    @Test
    fun `java source bigger than target`() {
        diagnosticsTest("java-source-bigger-than-target")
    }

    @Test
    fun `java source bigger than target in other context`() {
        diagnosticsTest("java-source-bigger-than-target-in-other-context")
    }

    @Test
    fun `java source bigger than implicit Java target`() {
        diagnosticsTest("java-source-bigger-than-implicit-java-target")
    }
}