/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */


import iosUtils.IOSBaseTest
import org.junit.jupiter.api.Test

class RuniOSExamplesOnEmulatorsTestsStandalone : IOSBaseTest() {

    @Test
    fun composeiOSAppStandalone() = runIosAppTests(
        projectSource = ProjectSource.Local("compose-ios"),
        bundleIdentifier = "compose-ios",
    )

    @Test
    fun composeiOSAppMultiplatform() = runIosAppTests(
        projectSource = ProjectSource.Local("compose-multiplatform"),
        bundleIdentifier = "ios-app",
        iosAppModuleName = "ios-app",
    )
}
