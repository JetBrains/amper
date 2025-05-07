/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */


import iosUtils.IOSBaseTest
import org.junit.jupiter.api.Test

class RuniOSExamplesOnEmulatorsTestsStandalone : IOSBaseTest() {

    @Test
    fun composeiOSAppStandalone() = testRunnerStandalone(
        projectSource = ProjectSource.Local("compose-ios"),
        bundleIdentifier = "compose-ios",
    )

    @Test
    fun composeiOSAppMultiplatform() = testRunnerStandalone(
        projectSource = ProjectSource.Local("compose-multiplatform"),
        bundleIdentifier = "ios-app",
        iosAppModuleName = "ios-app",
    )
}
