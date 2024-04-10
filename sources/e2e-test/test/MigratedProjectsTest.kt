/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledOnOs
import org.junit.jupiter.api.condition.OS
import kotlin.test.Ignore

class MigratedProjectsTest : E2ETestFixture("../../migrated-projects/", runWithPluginClasspath = false) {
    @Test
    fun `compose-multiplatform-ios-android-template (android)`() = test(
        projectName = "compose-multiplatform-ios-android-template",
        ":androidApp:assembleDebug",
        expectOutputToHave = "BUILD SUCCESSFUL"
    )

    @Test
    @EnabledOnOs(value = [OS.MAC])
    @KonanFolderLock
    fun `compose-multiplatform-ios-android-template (ios)`() = test(
        projectName = "compose-multiplatform-ios-android-template",
        ":shared:linkPodDebugFrameworkIosSimulatorArm64",
        expectOutputToHave = "BUILD SUCCESSFUL"
    )

    @Test
    fun `compose-multiplatform-desktop-template`() = test(
        projectName = "compose-multiplatform-desktop-template",
        "build",
        expectOutputToHave = "BUILD SUCCESSFUL"
    )

    @Test
    @KonanFolderLock
    fun `compose-multiplatform-template`() = test(
        projectName = "compose-multiplatform-template",
        "build",
        expectOutputToHave = "BUILD SUCCESSFUL"
    )

    @Test
    @EnabledOnOs(value = [OS.MAC])
    @KonanFolderLock
    fun `compose-multiplatform-template (ios)`() = test(
        projectName = "compose-multiplatform-template",
        ":shared:linkDebugFrameworkIosSimulatorArm64",
        expectOutputToHave = "BUILD SUCCESSFUL"
    )

    /**
     * TODO Investigate
     * Somehow mergeDesc fails, despite green build outside of test.
     *
     * Caused by: java.lang.AssertionError
     * 	 at com.android.tools.r8.synthesis.F.b(R8_8.1.56_756d1f50f618dd1c39c000f11defb367a21e9e866e3401b884be16c0950f6f79:27)
     *
     * Flaky on CI: org.gradle.api.tasks.TaskExecutionException: Execution failed for task ':shared:testReleaseUnitTest'.
     */
    @Test
    @Ignore
    @KonanFolderLock
    fun `KaMPKit build successful`() = test(
        projectName = "KaMPKit",
        "build",
        expectOutputToHave = "BUILD SUCCESSFUL"
    )
}