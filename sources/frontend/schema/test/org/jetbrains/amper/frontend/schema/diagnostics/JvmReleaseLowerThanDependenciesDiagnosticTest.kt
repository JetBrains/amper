/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.schema.diagnostics

import org.jetbrains.amper.frontend.schema.helper.diagnosticsTest
import org.jetbrains.amper.problems.reporting.Level
import org.jetbrains.amper.test.golden.GoldenTestBase
import kotlin.io.path.Path
import kotlin.io.path.div
import kotlin.test.Test

class JvmReleaseLowerThanDependenciesDiagnosticTest : GoldenTestBase(Path("testResources") / "diagnostics" / "jvm-release-mismatch") {

    @Test
    fun `jvm release lower than dependencies`() {
        diagnosticsTest(
            "lib-a/module",
            levels = arrayOf(Level.Warning, Level.Error, Level.Fatal),
            additionalFiles = listOf(
                "lib-b/module.yaml",
                "lib-c/module.yaml",
            ),
        )
    }
}
