/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.schema.diagnostics

import org.jetbrains.amper.test.golden.GoldenTestBase
import org.jetbrains.amper.frontend.schema.helper.diagnosticsTest
import kotlin.io.path.Path
import kotlin.io.path.div
import kotlin.test.Test

class ProductDiagnosticsTest : GoldenTestBase(Path("testResources") / "diagnostics" / "product") {
    @Test
    fun `product with unsupported type `() {
        diagnosticsTest("product-with-unsupported-type")
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

    @Test
    fun `product without type`() {
        diagnosticsTest("product-without-type")
    }

    @Test
    fun `empty module`() {
        diagnosticsTest("empty-module")
    }
}