/*
 * Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend

import org.jetbrains.amper.frontend.helper.aomTest
import org.jetbrains.amper.frontend.helper.copyLocal
import org.jetbrains.amper.frontend.helper.diagnosticsTest
import org.jetbrains.amper.frontend.old.helper.TestBase
import kotlin.io.path.Path
import kotlin.io.path.div
import kotlin.test.Test

internal class RepositoriesTest : TestBase(Path("testResources") / "repositories") {

    @Test
    fun `parsing id and url `() {
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
}
