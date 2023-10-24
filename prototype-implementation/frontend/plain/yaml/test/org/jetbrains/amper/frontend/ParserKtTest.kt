package org.jetbrains.amper.frontend

import org.jetbrains.amper.frontend.helper.AbstractTestWithBuildFile
import org.jetbrains.amper.frontend.helper.testParse
import kotlin.test.Test

internal class ParserKtTest : AbstractTestWithBuildFile() {
    
    @Test
    fun `single platform`() {
        withBuildFile {
            testParse("0-single-platform")
        }
    }

    @Test
    fun `multiplatform app`() {
        withBuildFile {
            testParse("1-multiplatform-app")
        }
    }

    @Test
    fun aliases() {
        withBuildFile {
            testParse("2-aliases") {
                directory("iosSimulator")
            }
        }
    }

    @Test
    fun variants() {
        withBuildFile {
            testParse("3-variants")
        }
    }

    @Test
    fun `jvm run`() {
        withBuildFile {
            testParse("4-jvm-run")
        }
    }

    @Test
    fun `common folder bug`() {
        withBuildFile {
            testParse("5-common-folder-bug") {
                directory("common") {
                    directory("src")
                }
            }
        }
    }

    @Test
    fun `empty key`() {
        withBuildFile {
            testParse("6-empty-list-key")
        }
    }

    @Test
    fun `variants even more simplified`() {
        withBuildFile {
            testParse("8-variants-simplified")
        }
    }

    @Test
    fun `test-dependencies is the same as dependencies@test`() {
        withBuildFile {
            testParse("9-test-dependencies")
        }
    }

    @Test
    fun `android properties should be passed in lib`() {
        withBuildFile {
            testParse("10-android-lib")
        }
    }

    @Test
    fun `plain frontend dogfooding`() {
        withBuildFile {
            testParse("11-frontend-plain")
        }
    }

    @Test
    fun `multiplatform library`() {
        withBuildFile {
            testParse("12-multiplatform-lib")
        }
    }

    @Test
    fun `jvmTarget adds to artifacts in android and jvm platforms`() {
        withBuildFile {
            testParse("13-multiplatform-jvmtarget")
        }
    }

    @Test
    fun `check artifacts of multi-variant builds`() {
        withBuildFile {
            testParse("14-check-artifacts-of-multi-variant-build")
        }
    }

    @Test
    fun `compose full form`() {
        withBuildFile {
            testParse("compose-full-form")
        }
    }

    @Test
    fun `compose inline form`() {
        withBuildFile {
            testParse("compose-inline-form")
        }
    }

    @Test
    fun `check kotlin serialization settings`() {
        withBuildFile {
            testParse("20-kotlin-serialization")
        }
    }

    @Test
    fun `check android sdk version`() {
        withBuildFile {
            testParse("21-android-sdk-version")
        }
    }
}
