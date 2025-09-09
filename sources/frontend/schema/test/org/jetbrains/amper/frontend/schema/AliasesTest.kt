/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.schema

import org.jetbrains.amper.frontend.schema.helper.aomTest
import org.jetbrains.amper.test.golden.GoldenTestBase
import org.junit.jupiter.api.Test
import kotlin.io.path.Path
import kotlin.io.path.div

class AliasesTest : GoldenTestBase(Path("testResources") / "parser" / "aliases") {
    @Test
    fun `regular alias`() {
        aomTest("regular-alias")
    }

    @Test
    fun `empty aliases`() {
        aomTest(
            "empty-aliases",
            expectedError = "Alias emptyAlias should have at least one platform present in the current product",
        )
    }

    @Test
    fun `skipped alias`() {
        aomTest(
            "skipped-alias",
            expectedError = "Alias skippedAlias should have at least one platform present in the current product",
        )
    }

    @Test
    fun `expanded alias`() {
        aomTest(
            "expanded-alias",
            expectedError = "Alias iosAndAndroid uses non-leaf platform ios, which is not supported at the moment"
        )
    }
}