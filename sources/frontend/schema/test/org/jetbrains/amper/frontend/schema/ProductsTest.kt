/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.schema

import org.jetbrains.amper.frontend.schema.helper.aomTest
import org.jetbrains.amper.test.golden.GoldenTestBase
import kotlin.io.path.Path
import kotlin.io.path.div
import kotlin.test.Test

internal class ProductsTest : GoldenTestBase(Path("testResources") / "product") {
    @Test
    fun `product lib `() {
        aomTest("product-lib")
    }

    @Test
    fun `product app inline`() {
        aomTest("product-app-inline")
    }

    @Test
    fun `product app with platforms`() {
        aomTest("product-app-with-platforms")
    }

    @Test
    fun `product app without platforms`() {
        aomTest("product-app-without-platforms")
    }

    @Test
    fun `product app multiple platforms`() {
        aomTest("product-app-with-multiple-platforms")
    }

    @Test
    fun `product app with non default platforms`() {
        aomTest("product-app-with-non-default-platforms")
    }
}
