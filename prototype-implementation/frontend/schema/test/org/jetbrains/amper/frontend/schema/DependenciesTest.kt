/*
 * Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.schema

import org.jetbrains.amper.frontend.old.helper.TestBase
import org.jetbrains.amper.frontend.schema.helper.aomTest
import kotlin.io.path.Path
import kotlin.io.path.div
import kotlin.test.Test

internal class DependenciesTest : TestBase(Path("testResources") / "dependencies") {

    @Test
    fun exported() {
        aomTest("dependency-flags-exported")
    }

    @Test
    fun `compile runtime`() {
        aomTest("dependency-flags-runtime-compile")
    }
}
