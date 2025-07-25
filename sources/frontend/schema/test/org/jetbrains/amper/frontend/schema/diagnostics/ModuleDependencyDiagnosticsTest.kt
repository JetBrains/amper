/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.schema.diagnostics

import org.jetbrains.amper.frontend.schema.helper.diagnosticsTest
import org.jetbrains.amper.test.golden.GoldenTestBase
import kotlin.io.path.Path
import kotlin.io.path.div
import kotlin.test.Test

class ModuleDependencyDiagnosticsTest : GoldenTestBase(Path("testResources") / "diagnostics" / "module-dependency") {
    @Test
    fun `product with unsupported type`() {
        diagnosticsTest(
            "needed-platforms/module",
            additionalFiles = listOf("needed-platforms/sub-module/module.yaml"),
        )
    }

    @Test
    fun `module dependency loops - lib-b`() {
        diagnosticsTest(
            "loops/lib-b/module",
            additionalFiles = listOf(
                "loops/lib-a/module.yaml",
                "loops/lib-c/module.yaml",
            ),
        )
    }

    @Test
    fun `module dependency loops - lib-a`() {
        diagnosticsTest(
            "loops/lib-a/module",
            additionalFiles = listOf(
                "loops/lib-b/module.yaml",
                "loops/lib-c/module.yaml",
            ),
        )
    }

    @Test
    fun `module dependency loops - lib-c`() {
        diagnosticsTest(
            "loops/lib-c/module",
            additionalFiles = listOf(
                "loops/lib-b/module.yaml",
                "loops/lib-a/module.yaml",
            ),
        )
    }

    @Test
    fun `unresolved module`() {
        diagnosticsTest("unresolved-module/module")
    }

    @Test
    fun `unresolved module with hint`() {
        diagnosticsTest(
            "unresolved-module-with-hint/module",
            additionalFiles = listOf(
                "unresolved-module-with-hint/inner/module2/module.yaml",
            )
        )
    }
}
