/*
 * Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.schema

import org.jetbrains.amper.frontend.old.helper.TestBase
import org.jetbrains.amper.frontend.schema.helper.aomTest
import org.jetbrains.amper.frontend.schema.helper.diagnosticsTest
import kotlin.io.path.Path
import kotlin.io.path.div
import kotlin.test.Test

internal class ProductsTest : TestBase(Path("testResources") / "product") {

    @Test
    fun `product with unsupported type `() {
        diagnosticsTest("product-with-unsupported-type")
    }

    @Test
    fun `product lib `() {
        aomTest("product-lib")
    }

    @Test
    fun `product lib without platforms full`() {
        diagnosticsTest("product-lib-without-platforms-full")
    }

    @Test
    fun `product lib without platforms inline`() {
        diagnosticsTest("product-lib-without-platforms-inline")
    }

    @Test
    fun `product lib with empty platforms inline`() {
        diagnosticsTest("product-lib-with-empty-platforms")
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

    @Test
    fun `product with incompatible platforms`() {
        diagnosticsTest("product-with-incompatible-platforms")
    }

    @Test
    fun `product with single unsupported platform`() {
        diagnosticsTest("product-with-single-unsupported-platform")
    }

    @Test
    fun `product with multiple unsupported platforms`() {
        diagnosticsTest("product-with-multiple-unsupported-platforms")
    }

}
