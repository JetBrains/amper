/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */


import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import kotlin.io.path.deleteRecursively

class RunAndroidExamplesOnEmulatorsTestsStandalone : AndroidBaseTest() {

    @Test
    fun composeAndroidAppGradle() = testRunnerStandalone(
        projectName = "simple",
    )

    @Test
    fun composeAndroidAppAppCompat() = testRunnerStandalone(
        projectName = "appcompat",
    )

    @Test
    fun parcelizeAndroidApp() = testRunnerStandalone(
        projectName = "parcelize",
    )

    @AfterEach
    fun cleanup() {
        tempProjectsDir.deleteRecursively()
        runBlocking {
            deleteAdbRemoteSession()
        }
    }

}