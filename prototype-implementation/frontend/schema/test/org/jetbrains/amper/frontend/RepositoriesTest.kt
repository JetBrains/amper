/*
 * Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend

import org.jetbrains.amper.frontend.old.helper.TestWithBuildFile
import kotlin.test.Ignore
import kotlin.test.Test

internal class RepositoriesTest : TestWithBuildFile() {

    // TODO Fix
    @Test
    @Ignore
    fun `parsing id and url `() {
        withBuildFile {
//            testParseWithTemplates("repositories-id-and-url")
        }
    }

    // TODO Fix
    @Test
    @Ignore
    fun `parsing credentials`() {
        withBuildFile {
//            testParseWithTemplates("repositories-credentials") {
//                copyLocal("repositories-credentials.local.properties")
//            }
        }
    }

    // TODO Fix
    @Test
    @Ignore
    fun `repositories no credentials file`() {
        withBuildFile {
//            testParseWithTemplates("repositories-no-credentials-file")
        }
    }
}
