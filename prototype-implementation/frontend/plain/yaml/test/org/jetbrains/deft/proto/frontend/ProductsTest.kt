package org.jetbrains.deft.proto.frontend

import org.jetbrains.deft.proto.frontend.helper.AbstractTestWithBuildFile
import org.jetbrains.deft.proto.frontend.helper.testParse
import kotlin.test.Test

internal class ProductsTest : AbstractTestWithBuildFile() {

    @Test
    fun `product with unsupported type `() {
        withBuildFile {
            testParse("product-with-unsupported-type")
        }
    }


    @Test
    fun `product lib `() {
        withBuildFile {
            testParse("product-lib")
        }
    }

    @Test
    fun `product lib without platforms full`() {
        withBuildFile {
            testParse("product-lib-without-platforms-full")
        }
    }

    @Test
    fun `product lib without platforms inline`() {
        withBuildFile {
            testParse("product-lib-without-platforms-inline")
        }
    }

    @Test
    fun `product lib with empty platforms inline`() {
        withBuildFile {
            testParse("product-lib-with-empty-platforms")
        }
    }

    @Test
    fun `product app inline`() {
        withBuildFile {
            testParse("product-app-inline")
        }
    }

    @Test
    fun `product app with platforms`() {
        withBuildFile {
            testParse("product-app-with-platforms")
        }
    }

    @Test
    fun `product app without platforms`() {
        withBuildFile {
            testParse("product-app-without-platforms")
        }
    }

    @Test
    fun `product app multiple platforms`() {
        withBuildFile {
            testParse("product-app-with-multiple-platforms")
        }
    }

    @Test
    fun `product app with non default platforms`() {
        withBuildFile {
            testParse("product-app-with-non-default-platforms")
        }
    }

    @Test
    fun `product with incompatible platforms`() {
        withBuildFile {
            testParse("product-with-incompatible-platforms")
        }
    }

    @Test
    fun `product with single unsupported platform`() {
        withBuildFile {
            testParse("product-with-single-unsupported-platform")
        }
    }

    @Test
    fun `product with multiple unsupported platforms`() {
        withBuildFile {
            testParse("product-with-multiple-unsupported-platforms")
        }
    }

}
