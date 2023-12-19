/*
 * Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend

import org.jetbrains.amper.frontend.helper.aomTest
import org.jetbrains.amper.frontend.helper.diagnosticsTest
import org.jetbrains.amper.frontend.old.helper.TestBase
import kotlin.test.Test

internal class CatalogTest : TestBase() {

    @Test
    fun `check build in compose catalog versions`() = withBuildFile {
        aomTest("build-in-compose-catalogue")
    }

    @Test
    fun `check failure with absent catalog key`() = withBuildFile {
        diagnosticsTest("no-catalog-value")
    }
}
