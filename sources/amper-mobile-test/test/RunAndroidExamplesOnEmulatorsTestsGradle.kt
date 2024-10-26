/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */


import org.junit.jupiter.api.Test

class RunAndroidExamplesOnEmulatorsTestsGradle : AndroidBaseTest() {

    @Test
    fun composeAndroidAppGradle() = testRunnerGradle(
        projectName = "compose-android",
    )

    @Test
    fun composeAndroidMultiplatformAppGradle() = testRunnerGradle(
        projectName = "multiplatform",
    )
}