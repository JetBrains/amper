/*
 * Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend

import org.jetbrains.amper.frontend.helper.aomTest
import org.jetbrains.amper.frontend.helper.copyLocal
import org.jetbrains.amper.frontend.helper.diagnosticsTest
import org.jetbrains.amper.frontend.old.helper.TestWithBuildFile
import kotlin.test.Test

internal class RepositoriesTest : TestWithBuildFile() {

    @Test
    fun `parsing id and url `() {
        withBuildFile {
            aomTest("repositories-id-and-url")
        }
    }

    @Test
    fun `parsing credentials`() {
        withBuildFile {
            copyLocal("repositories-credentials.local.properties", buildDir)
            aomTest("repositories-credentials")
        }
    }

    @Test
    fun `repositories no credentials file`() {
        withBuildFile {
            diagnosticsTest("repositories-no-credentials-file")
        }
    }
}
