/*
 * Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend

import org.jetbrains.amper.frontend.old.helper.TestWithBuildFile
import kotlin.test.Ignore
import kotlin.test.Test

internal class TemplatesTest : TestWithBuildFile() {

    // TODO Fix
    @Test
    @Ignore
    fun `check artifacts of multi-variant builds`() {
        withBuildFile {
//            testParseWithTemplates("templates-simple")
        }
    }

    // TODO Fix
    @Test
    @Ignore
    fun `check path literals are adjusted`() {
        withBuildFile {
//            testParseWithTemplates("templates-adjust-path-test")
        }
    }

    // TODO Fix
    @Test
    @Ignore
    fun `empty template file`() {
        withBuildFile {
//            testParseWithTemplates("templates-empty-file")
        }
    }

    // TODO Fix
    @Test
    @Ignore
    fun `empty apply list file`() {
        withBuildFile {
//            testParseWithTemplates("templates-empty-apply")
        }
    }
}
