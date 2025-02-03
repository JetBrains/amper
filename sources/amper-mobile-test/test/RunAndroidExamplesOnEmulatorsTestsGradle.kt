/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */


import androidUtils.AndroidBaseTest
import org.junit.jupiter.api.Test
import kotlin.test.Ignore

class RunAndroidExamplesOnEmulatorsTestsGradle : AndroidBaseTest() {

    @Test
    @Ignore("Unignore after relevant changes in `git.jetbrains.team/amper/amper-external-projects.git`")
    fun composeAndroidAppGradle() = testRunnerGradle(
        projectName = "compose-android",
    )

    @Test
    @Ignore("Unignore after relevant changes in `git.jetbrains.team/amper/amper-external-projects.git`")
    fun composeAndroidMultiplatformAppGradle() = testRunnerGradle(
        projectName = "multiplatform",
        androidAppSubprojectName = "android-app",
    )
}