/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.schema

import org.jetbrains.amper.core.system.Arch
import org.jetbrains.amper.core.system.OsFamily
import org.jetbrains.amper.core.system.SystemInfo
import org.jetbrains.amper.frontend.schema.helper.TestSystemInfo
import org.jetbrains.amper.frontend.schema.helper.aomTest
import org.jetbrains.amper.test.golden.GoldenTestBase
import kotlin.io.path.Path
import kotlin.io.path.div
import kotlin.test.Ignore
import kotlin.test.Test

internal class ParserKtTest : GoldenTestBase(Path("testResources") / "parser") {

    @Test
    fun `single platform`() {
        aomTest("0-single-platform")
    }

    @Test
    fun `multiplatform app`() {
        aomTest("1-multiplatform-app")
    }

    @Test
    fun `multiplatform native settings`() {
        aomTest("multiplatform-native-settings")
    }

    // TODO See: https://youtrack.jetbrains.com/issue/AMPER-114/Change-aliases-DSL-from-object-to-list
    @Test
    fun aliases() {
        aomTest("2-aliases")
    }

    @Test
    @Ignore
    fun variants() {
        aomTest("3-variants")
    }

    @Test
    fun `jvm run`() {
        aomTest("4-jvm-run")
    }

    // TODO Fix
    @Test
    @Ignore
    fun `common folder bug`() {
        aomTest("5-common-folder-bug")
//            {
//                directory("common") {
//                    directory("src")
//                }
//            }
    }

    // TODO Support the case.
    // TODO Add warning to report.
    @Test
    fun `empty key`() {
        aomTest("6-empty-list-key")
    }

    // TODO Add variants issue link.
    @Test
    @Ignore
    fun `variants even more simplified`() {
        aomTest("8-variants-simplified")
    }

    @Test
    fun `test-dependencies is the same as dependencies@test`() {
        aomTest("9-test-dependencies")
    }

    @Test
    fun `android properties should be passed in lib`() {
        aomTest("10-android-lib")
    }

    @Test
    fun `plain frontend dogfooding`() {
        aomTest(
            "11-frontend-plain",
            // TODO: Rewrite this test to properly reflect the project structure
            expectedError = "Cannot find a module file '../frontend-api'"
        )
    }

    @Test
    fun `multiplatform library`() {
        aomTest("12-multiplatform-lib")
    }

    @Test
    fun `jvmTarget adds to artifacts in android and jvm platforms`() {
        aomTest("13-multiplatform-jvmrelease")
    }

    @Test
    @Ignore
    fun `check artifacts of multi-variant builds`() {
        aomTest("14-check-artifacts-of-multi-variant-build")
    }

    @Test
    fun `compose full form`() {
        aomTest("compose-full-form")
    }

    @Test
    fun `compose inline form`() {
        aomTest("compose-inline-form")
    }

    @Test
    fun `common library replacement`() {
        println()
        aomTest(
            "17-compose-desktop-replacement",
            TestSystemInfo(SystemInfo.Os(OsFamily.MacOs, "X", Arch.X64))
        )
    }

    @Test
    fun `jvm library replacement`() {
        aomTest(
            "18-compose-desktop-jvm-replacement",
            TestSystemInfo(SystemInfo.Os(OsFamily.Linux, "3.14", Arch.Arm64))
        )
    }

    @Test
    fun `add kotlin-test automatically`() {
        aomTest("19-compose-android-without-tests")
    }

    @Test
    fun `check kotlin serialization settings`() {
        aomTest("20-kotlin-serialization")
    }

    @Test
    fun `check kotlin serialization settings with custom version`() {
        aomTest("kotlin-serialization-custom-version")
    }

    @Test
    fun `check kotlin serialization settings with 'enabled' short form`() {
        aomTest("kotlin-serialization-enabled")
    }

    @Test
    fun `check kotlin serialization settings with 'none' short form`() {
        aomTest("kotlin-serialization-none")
    }

    @Test
    fun `check kotlin serialization settings with enabled and format`() {
        aomTest("kotlin-serialization-enabled-and-format")
    }

    @Test
    fun `check android sdk version`() {
        aomTest("21-android-sdk-version")
    }

    @Test
    fun `android namespace set`() {
        aomTest("android-namespace-setting")
    }

    @Test
    fun coverage() {
        aomTest("22-coverage")
    }

    @Test
    fun `coverage short form`() {
        aomTest("23-coverage-short-form")
    }

    @Test
    fun `no NPE when leaf platform is accessed`() {
        aomTest(
            "24-no-npe-for-leaf-platform",
            // TODO: Rewrite this test to properly reflect the project structure
            expectedError = "Cannot find a module file './subModule'"
        )
    }

    @Test
    fun tasks() {
        aomTest("tasks")
    }

    @Test
    fun ksp() {
        aomTest("ksp")
    }

    @Test
    fun `parcelize enabled`() {
        aomTest("parcelize-enabled")
    }

    @Test
    fun `parcelize options`() {
        aomTest("parcelize-options")
    }

    @Test
    fun `test with android version override`() {
        aomTest("overriding-android-sdk-compile-version", expectedError = "Version for compileSdk (27) should be at least minSdk version (30)")
    }

    @Test
    fun `compose hot reload sets runtime classpath mode to classes`() {
        aomTest("compose-hot-reload")
    }
}
