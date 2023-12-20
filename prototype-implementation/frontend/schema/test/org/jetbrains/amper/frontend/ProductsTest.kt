/*
 * Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend

import org.jetbrains.amper.frontend.helper.aomTest
import org.jetbrains.amper.frontend.helper.diagnosticsTest
import org.jetbrains.amper.frontend.old.helper.TestBase
import kotlin.io.path.Path
import kotlin.io.path.div
import kotlin.test.Test

internal class ProductsTest : TestBase(Path("testResources") / "product") {

    @Test
    fun `product with unsupported type `() {
        withBuildFile {
            diagnosticsTest("product-with-unsupported-type")
        }
    }

    @Test
    fun `product lib `() {
        withBuildFile {
            aomTest("product-lib")
        }
    }

    @Test
    fun `product lib without platforms full`() {
        withBuildFile {
            diagnosticsTest("product-lib-without-platforms-full")
        }
    }

    @Test
    fun `product lib without platforms inline`() {
        withBuildFile {
            diagnosticsTest("product-lib-without-platforms-inline")
        }
    }

    @Test
    fun `product lib with empty platforms inline`() {
        withBuildFile {
            diagnosticsTest("product-lib-with-empty-platforms")
        }
    }

    @Test
    fun `product app inline`() {
        withBuildFile {
            aomTest("product-app-inline")
        }
    }

    @Test
    fun `product app with platforms`() {
        withBuildFile {
            aomTest("product-app-with-platforms")
        }
    }

    @Test
    fun `product app without platforms`() {
        withBuildFile {
            aomTest("product-app-without-platforms")
        }
    }

    @Test
    fun `product app multiple platforms`() {
        withBuildFile {
            aomTest("product-app-with-multiple-platforms")
        }
    }

    @Test
    fun `product app with non default platforms`() {
        withBuildFile {
            aomTest("product-app-with-non-default-platforms")
        }
    }

    @Test
    fun `product with incompatible platforms`() {
        withBuildFile {
            diagnosticsTest("product-with-incompatible-platforms")
        }
    }

    @Test
    fun `product with single unsupported platform`() {
        withBuildFile {
            diagnosticsTest("product-with-single-unsupported-platform")
        }
    }

    @Test
    fun `product with multiple unsupported platforms`() {
        withBuildFile {
            diagnosticsTest("product-with-multiple-unsupported-platforms")
        }
    }

}
