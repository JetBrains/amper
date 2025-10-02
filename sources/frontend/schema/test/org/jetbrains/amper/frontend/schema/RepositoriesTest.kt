/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.schema

import org.jetbrains.amper.frontend.helpers.FrontendTestCaseBase
import org.jetbrains.amper.frontend.helpers.aomTest
import org.jetbrains.amper.frontend.helpers.diagnosticsTest
import kotlin.io.path.Path
import kotlin.io.path.div
import kotlin.test.Test

internal class RepositoriesTest : FrontendTestCaseBase(Path("testResources") / "repositories") {

    @Test
    fun `parsing id and url`() {
        aomTest("repositories-id-and-url")
    }

    @Test
    fun `parsing credentials`() {
        copyLocalToBuild("repositories-credentials.local.properties")
        aomTest("repositories-credentials")
    }

    @Test
    fun `repositories no credentials file`() {
        diagnosticsTest("repositories-no-credentials-file")
    }

    @Test
    fun `repositories no credential key`() {
        copyLocalToBuild("repositories-credentials.local.properties")
        diagnosticsTest("repositories-no-credentials-key")
    }
}
