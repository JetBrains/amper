/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */


import iosUtils.IOSBaseTest
import org.junit.jupiter.api.Test

class RuniOSExternalProjectsStandalone : IOSBaseTest() {

    @Test
    fun kotlinConfAppTest() = testRunnerStandalone(
        projectName = "kotlinconf",
        bundleIdentifier = "ios-app",
        iosAppModuleName = "ios-app",
    )

    @Test
    fun toDoListApp() = testRunnerStandalone(
        projectName = "todolistlite",
        bundleIdentifier = "ios-app",
        iosAppModuleName = "ios-app",
    )

    @Test
    fun recipeApp() = testRunnerStandalone(
        projectName = "recipeapp",
        bundleIdentifier = "ios-app",
        iosAppModuleName = "ios-app",
    )

    @Test
    fun swiftAppWithoutShared() = testRunnerStandalone(
        projectName = "swiftonlytodo",
        bundleIdentifier = "swiftonlytodo",
    )
}
