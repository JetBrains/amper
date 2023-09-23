package org.jetbrains.deft.proto.frontend

import org.jetbrains.deft.proto.frontend.helper.AbstractTestWithBuildFile
import org.jetbrains.deft.proto.frontend.helper.assertHasSingleProblem
import org.jetbrains.deft.proto.frontend.helper.testParse
import org.junit.jupiter.api.Assertions.assertTrue
import kotlin.test.Test
import kotlin.test.assertEquals

internal class ProductsTest : AbstractTestWithBuildFile() {

    @Test
    fun `product with unsupported type `() {
        withBuildFile {
            testParse("product-with-unsupported-type", checkErrors = { problems ->
                problems.assertHasSingleProblem {
                    assertTrue("foo/bar" in message, message)
                    assertTrue("Unsupported product type" in message, message)
                    assertTrue("lib" in message, message)
                    assertTrue("jvm/app" in message, message)
                }
            })
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
            testParse("product-lib-without-platforms-full", checkErrors = { problems ->
                problems.assertHasSingleProblem {
                    assertTrue(message.startsWith("Product type lib should have its platforms declared "), message)
                }
            })
        }
    }

    @Test
    fun `product lib without platforms inline`() {
        withBuildFile {
            testParse("product-lib-without-platforms-inline", checkErrors = { problems ->
                problems.assertHasSingleProblem {
                    assertTrue(message.startsWith("Product type lib should have its platforms declared "), message)
                }
            })
        }
    }

    @Test
    fun `product lib with empty platforms inline`() {
        withBuildFile {
            testParse("product-lib-with-empty-platforms", checkErrors = { problems ->
                problems.assertHasSingleProblem {
                    assertEquals("Product platforms list should not be empty.", message)
                }
            })
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
            testParse("product-with-incompatible-platforms", checkErrors = { problems ->
                problems.assertHasSingleProblem {
                    assertTrue("jvm/app" in message, message)
                    assertTrue("iosArm64" in message, message)
                    assertTrue("Only supported platforms are: [jvm]" in message, message)
                }
            })
        }
    }

    @Test
    fun `product with single unsupported platform`() {
        withBuildFile {
            testParse("product-with-single-unsupported-platform", checkErrors = { problems ->
                problems.assertHasSingleProblem {
                    assertEquals("Unknown platform: foo.", message)
                }
            })
        }
    }

    @Test
    fun `product with multiple unsupported platforms`() {
        withBuildFile {
            testParse("product-with-multiple-unsupported-platforms", checkErrors = { problems ->
                assertTrue(problems.size == 2)
                problems[0].apply {
                    assertEquals("Unknown platform: foo.", message)
                }
                problems[1].apply {
                    assertEquals("Unknown platform: bar.", message)
                }
            })
        }
    }

}
