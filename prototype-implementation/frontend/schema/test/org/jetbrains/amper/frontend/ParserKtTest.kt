/*
 * Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend

import org.jetbrains.amper.frontend.helper.aomTest
import org.jetbrains.amper.frontend.old.helper.TestWithBuildFile
import kotlin.test.Ignore
import kotlin.test.Test

internal class ParserKtTest : TestWithBuildFile() {
    
    @Test
    fun `single platform`() {
        withBuildFile {
            aomTest("0-single-platform")
        }
    }

    @Test
    fun `multiplatform app`() {
        withBuildFile {
            aomTest("1-multiplatform-app")
        }
    }

    // TODO See: https://youtrack.jetbrains.com/issue/AMPER-114/Change-aliases-DSL-from-object-to-list
    @Test
    fun aliases() {
        withBuildFile {
            aomTest("2-aliases")
        }
    }

    @Test
    @Ignore
    fun variants() {
        withBuildFile {
            aomTest("3-variants")
        }
    }

    @Test
    fun `jvm run`() {
        withBuildFile {
            aomTest("4-jvm-run")
        }
    }

    // TODO Fix
    @Test
    @Ignore
    fun `common folder bug`() {
        withBuildFile {
            aomTest("5-common-folder-bug")
//            {
//                directory("common") {
//                    directory("src")
//                }
//            }
        }
    }

    // TODO Support the case.
    // TODO Add warning to report.
    @Test
    fun `empty key`() {
        withBuildFile {
            aomTest("6-empty-list-key")
        }
    }

    // TODO Add variants issue link.
    @Test
    @Ignore
    fun `variants even more simplified`() {
        withBuildFile {
            aomTest("8-variants-simplified")
        }
    }

    @Test
    fun `test-dependencies is the same as dependencies@test`() {
        withBuildFile {
            aomTest("9-test-dependencies")
        }
    }

    @Test
    fun `android properties should be passed in lib`() {
        withBuildFile {
            aomTest("10-android-lib")
        }
    }

    @Test
    fun `plain frontend dogfooding`() {
        withBuildFile {
            aomTest("11-frontend-plain")
        }
    }

    @Test
    fun `multiplatform library`() {
        withBuildFile {
            aomTest("12-multiplatform-lib")
        }
    }

    @Test
    fun `jvmTarget adds to artifacts in android and jvm platforms`() {
        withBuildFile {
            aomTest("13-multiplatform-jvmtarget")
        }
    }

    @Test
    @Ignore
    fun `check artifacts of multi-variant builds`() {
        withBuildFile {
            aomTest("14-check-artifacts-of-multi-variant-build")
        }
    }

    @Test
    fun `compose full form`() {
        withBuildFile {
            aomTest("compose-full-form")
        }
    }

    @Test
    fun `compose inline form`() {
        withBuildFile {
            aomTest("compose-inline-form")
        }
    }

    @Test
    fun `check kotlin serialization settings`() {
        withBuildFile {
            aomTest("20-kotlin-serialization")
        }
    }

    @Test
    fun `check android sdk version`() {
        withBuildFile {
            aomTest("21-android-sdk-version")
        }
    }

    @Test
    fun `android namespace set`() {
        withBuildFile {
            aomTest("android-namespace-setting")
        }
    }

    @Test
    fun `coverage`() {
        withBuildFile {
            aomTest("22-coverage")
        }
    }

    @Test
    fun `coverage short form`() {
        withBuildFile {
            aomTest("23-coverage-short-form")
        }
    }
}
