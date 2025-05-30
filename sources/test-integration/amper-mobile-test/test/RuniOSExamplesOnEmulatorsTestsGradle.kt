/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */


import iosUtils.IOSBaseTest
import org.junit.jupiter.api.Test

class RuniOSExamplesOnEmulatorsTestsGradle : IOSBaseTest() {

    @Test
    fun composeiOSAppGradle() = testRunnerGradle(
        projectSource = ProjectSource.Local("compose-ios"),
        bundleIdentifier = "iosApp.iosApp",
    )

    @Test
    fun composeAndroidMultiplatformAppGradle() = testRunnerGradle(
        projectSource = ProjectSource.Local("compose-multiplatform"),
        bundleIdentifier = "compose-multiplatform.iosApp.iosApp",
        iosAppSubprojectName = "ios-app",
    )
}
