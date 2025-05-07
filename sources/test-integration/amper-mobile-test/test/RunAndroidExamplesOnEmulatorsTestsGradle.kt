/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */


import androidUtils.AndroidBaseTest
import org.junit.jupiter.api.Test

class RunAndroidExamplesOnEmulatorsTestsGradle : AndroidBaseTest() {

    @Test
    fun composeAndroidAppGradle() = testRunnerGradle(
        projectSource = ProjectSource.Local("compose-android"),
    )

    @Test
    fun composeAndroidMultiplatformAppGradle() = testRunnerGradle(
        projectSource = ProjectSource.Local("multiplatform"),
        androidAppSubprojectName = "android-app",
    )
}