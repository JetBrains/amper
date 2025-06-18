/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.schema

import org.jetbrains.amper.frontend.schema.helper.aomTest
import org.jetbrains.amper.frontend.schema.helper.copyLocal
import org.jetbrains.amper.frontend.schema.helper.diagnosticsTest
import org.jetbrains.amper.test.golden.GoldenTestBase
import kotlin.io.path.Path
import kotlin.io.path.div
import kotlin.test.Test

internal class RepositoriesTest : GoldenTestBase(Path("testResources") / "repositories") {

    @Test
    fun `parsing id and url`() {
        aomTest("repositories-id-and-url")
    }

    @Test
    fun `parsing credentials`() {
        copyLocal("repositories-credentials.local.properties", buildDir)
        aomTest("repositories-credentials")
    }

    @Test
    fun `repositories no credentials file`() {
        diagnosticsTest("repositories-no-credentials-file")
    }

    @Test
    fun `repositories no credential key`() {
        copyLocal("repositories-credentials.local.properties", buildDir)
        diagnosticsTest("repositories-no-credentials-key")
    }
}
