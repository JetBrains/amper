/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */


import iosUtils.IOSBaseTest
import org.jetbrains.amper.test.Dirs
import org.junit.jupiter.api.Test
import kotlin.io.path.div

class RuniOSExamplesOnEmulatorsTestsStandalone : IOSBaseTest() {

    @Test
    fun composeiOSAppStandalone() = runIosAppTests(
        projectSource = ProjectSource.Local(Dirs.examplesRoot / "compose-ios"),
        bundleIdentifier = "compose-ios",
    )

    @Test
    fun composeiOSAppMultiplatform() = runIosAppTests(
        projectSource = ProjectSource.Local(Dirs.examplesRoot / "compose-multiplatform"),
        bundleIdentifier = "ios-app",
        iosAppModuleName = "ios-app",
    )
}
