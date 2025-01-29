/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */


import iosUtils.IOSBaseTest
import kotlinx.coroutines.runBlocking
import org.jetbrains.amper.test.MacOnly
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test

class RuniOSExamplesOnEmulatorsTestsGradle : IOSBaseTest() {

    @Test
    fun composeiOSAppGradle() = testRunnerGradle(
        projectName = "compose-ios",
        bundleIdentifier = "iosApp.iosApp",
    )

    @Test
    fun composeAndroidMultiplatformAppGradle() = testRunnerGradle(
        projectName = "compose-multiplatform",
        bundleIdentifier = "compose-multiplatform.iosApp.iosApp",
        iosAppSubprojectName = "ios-app",
    )
}
