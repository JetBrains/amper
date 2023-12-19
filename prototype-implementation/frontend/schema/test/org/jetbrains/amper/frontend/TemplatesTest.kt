/*
 * Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend

import org.jetbrains.amper.frontend.helper.aomTest
import org.jetbrains.amper.frontend.old.helper.TestWithBuildFile
import kotlin.test.Ignore
import kotlin.test.Test

internal class TemplatesTest : TestWithBuildFile() {

    // TODO Fix
    @Test
    @Ignore
    fun `check artifacts of multi-variant builds`() {
        withBuildFile {
            aomTest("templates-simple")
        }
    }

    @Test
    fun `check path literals are adjusted`() {
        withBuildFile {
            aomTest("templates-adjust-path-test")
        }
    }

    @Test
    fun `empty template file`() {
        withBuildFile {
            aomTest("templates-empty-file")
        }
    }

    @Test
    fun `empty apply list file`() {
        withBuildFile {
            aomTest("templates-empty-apply")
        }
    }
}
