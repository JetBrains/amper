/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledOnOs
import org.junit.jupiter.api.condition.OS
import java.nio.file.Path
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.deleteRecursively
import kotlin.io.path.div
import kotlin.io.path.exists
import kotlin.test.Ignore
import kotlin.test.assertTrue

class EmulatorTests : E2ETestFixture("./testData/projects/") {
    @Test
    fun `compose android ui tests task`() = test(
        projectName = "compose-android-ui",
        "connectedAndroidTest",
        expectOutputToHave = "BUILD SUCCESSFUL"
    )
}
