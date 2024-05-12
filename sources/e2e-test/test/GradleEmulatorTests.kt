/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

import org.junit.jupiter.api.Test

class GradleEmulatorTests : GradleE2ETestFixture("./testData/projects/") {
    @Test
    fun `compose android ui tests task`() = test(
        projectName = "compose-android-ui",
        "connectedAndroidTest",
        expectOutputToHave = "BUILD SUCCESSFUL"
    )
}
