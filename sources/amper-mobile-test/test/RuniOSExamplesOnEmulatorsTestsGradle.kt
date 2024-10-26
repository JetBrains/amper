/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */


import kotlinx.coroutines.runBlocking
import org.jetbrains.amper.test.MacOnly
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test

class RuniOSExamplesOnEmulatorsTestsGradle : iOSBaseTest() {

    @MacOnly
    @Test
    fun composeiOSAppGradle() = testRunnerGradle(
        projectName = "compose-ios",
    )

    @MacOnly
    @Test
    fun composeAndroidMultiplatformAppGradle() = testRunnerGradle(
        projectName = "compose-multiplatform",
    )

    @AfterEach
    fun cleanupSession() {
        runBlocking {
            deleteRemoteSession()
        }
    }
}
