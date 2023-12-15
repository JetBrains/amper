/*
 * Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend

import org.jetbrains.amper.frontend.helper.aomTest
import org.jetbrains.amper.frontend.old.helper.TestWithBuildFile
import kotlin.test.Test

class KotlinTestAddingTest : TestWithBuildFile() {
    @Test
    fun `add kotlin-test automatically`() {
        withBuildFile {
            aomTest("19-compose-android-without-tests")
        }
    }
}
