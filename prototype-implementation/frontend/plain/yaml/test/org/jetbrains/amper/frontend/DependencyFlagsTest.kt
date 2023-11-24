/*
 * Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend

import org.jetbrains.amper.frontend.helper.AbstractTestWithBuildFile
import org.jetbrains.amper.frontend.helper.testParse
import kotlin.test.Test

internal class DependencyFlagsTest : AbstractTestWithBuildFile() {

    @Test
    fun exported() {
        withBuildFile {
            testParse("dependency-flags-exported")
        }
    }

    @Test
    fun `compile runtime`() {
        withBuildFile {
            testParse("dependency-flags-runtime-compile")
        }
    }
}
