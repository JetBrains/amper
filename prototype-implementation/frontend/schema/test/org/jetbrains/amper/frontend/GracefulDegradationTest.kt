/*
 * Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend

import kotlin.test.Ignore
import kotlin.test.Test

class GracefulDegradationTest {

    @Test
    @Ignore
    // TODO Fill up more reporting before running this test.
    fun `broken module settings`() = moduleConvertTest("broken-module-settings")

}