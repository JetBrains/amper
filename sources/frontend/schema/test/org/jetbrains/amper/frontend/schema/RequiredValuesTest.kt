/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.schema

import org.jetbrains.amper.frontend.schema.helper.diagnosticsTest
import org.jetbrains.amper.test.golden.GoldenTestBase
import kotlin.io.path.Path
import kotlin.io.path.div
import kotlin.test.Test

class RequiredValuesTest : GoldenTestBase(Path("testResources") / "required-values") {
    @Test
    fun `invalid platforms`() {
        diagnosticsTest("0-invalid-platforms/module")
    }

    @Test
    fun `invalid repository url`() {
        diagnosticsTest("1-invalid-repository-url/module")
    }

    @Test
    fun `no credentials file`() {
        diagnosticsTest("2-no-credentials-file/module")
    }

    @Test
    fun `missing username key`() {
        diagnosticsTest("3-missing-username-key/module")
    }

    @Test
    fun `serialization enabled invalid`() {
        diagnosticsTest("4-serialization-enabled-invalid/module")
    }
}