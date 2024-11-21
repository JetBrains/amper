/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */


import iosUtils.IOSBaseTest
import org.junit.jupiter.api.Test

class RuniOSExamplesOnEmulatorsTestsStandalone : IOSBaseTest() {

    @Test
    fun composeiOSAppStandalone() = testRunnerStandalone(
        projectName = "compose-ios",
        bundleIdentifier = "iosSimulatorArm64.compose-ios",
    )

    @Test
    fun composeiOSAppMultiplatform() = testRunnerStandalone(
        projectName = "compose-multiplatform",
        bundleIdentifier = "iosSimulatorArm64.ios-app",
        iosAppModuleName = "ios-app",
    )

}
