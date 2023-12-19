/*
 * Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend

import org.jetbrains.amper.frontend.helper.convertTest
import org.jetbrains.amper.frontend.old.helper.TestBase
import kotlin.test.Ignore
import kotlin.test.Test

class GracefulDegradationTest : TestBase() {

    @Test
    @Ignore
    // TODO Fill up more reporting before running this test.
    fun `broken module settings`() = convertTest("broken-module-settings", "")

}