/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.cli.test

import org.jetbrains.amper.cli.test.utils.runSlowTest
import org.jetbrains.amper.test.MacOnly
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import kotlin.test.Test

// CONCURRENT is here to test that multiple concurrent amper processes work correctly.
@Execution(ExecutionMode.CONCURRENT)
class ComposeResourcesTest : AmperCliTestBase() {

    @Test
    fun `compose resources demo build (android)`() = runSlowTest {
        runCli(
            projectRoot = testProject("compose-resources-demo"),
            "task", ":app-android:buildAndroidDebug",
        )
    }

    @Test
    fun `compose resources demo build and run (jvm)`() = runSlowTest {
        runCli(
            projectRoot = testProject("compose-resources-demo"),
            "build", "--platform=jvm",
        )
        runCli(
            projectRoot = testProject("compose-resources-demo"),
            "test", "--platform=jvm",
            assertEmptyStdErr = false,  // on some platforms/machines, the UI part may issue warnings to stderr
        )
    }

    @Test
    @MacOnly
    fun `compose resources demo build (ios)`() = runSlowTest {
        runCli(
            projectRoot = testProject("compose-resources-demo"),
            "build", "--platform=iosSimulatorArm64",
            assertEmptyStdErr = false,  // xcodebuild prints a bunch of warnings (unrelated to resources) for now :(
            copyToTempDir = true,
        )
    }
}