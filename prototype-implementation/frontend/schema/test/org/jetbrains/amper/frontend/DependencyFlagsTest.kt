/*
 * Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend

import org.jetbrains.amper.frontend.helper.aomTest
import org.jetbrains.amper.frontend.old.helper.TestWithBuildFile
import kotlin.test.Test

internal class DependencyFlagsTest : TestWithBuildFile() {

    @Test
    fun exported() {
        withBuildFile {
            aomTest("dependency-flags-exported")
        }
    }

    @Test
    fun `compile runtime`() {
        withBuildFile {
            aomTest("dependency-flags-runtime-compile")
        }
    }
}
