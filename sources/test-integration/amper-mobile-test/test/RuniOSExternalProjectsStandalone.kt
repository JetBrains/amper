/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */


import iosUtils.IOSBaseTest
import org.junit.jupiter.api.Test

class RuniOSExternalProjectsStandalone : IOSBaseTest() {

    @Test
    fun kotlinConfAppTest() = testRunnerStandalone(
        projectSource = amperExternalProject("kotlinconf"),
        bundleIdentifier = "ios-app",
        iosAppModuleName = "ios-app",
    )

    @Test
    fun toDoListApp() = testRunnerStandalone(
        projectSource = amperExternalProject("todolistlite"),
        bundleIdentifier = "ios-app",
        iosAppModuleName = "ios-app",
    )

    @Test
    fun recipeApp() = testRunnerStandalone(
        projectSource = amperExternalProject("recipeapp"),
        bundleIdentifier = "ios-app",
        iosAppModuleName = "ios-app",
    )

    @Test
    fun swiftAppWithoutShared() = testRunnerStandalone(
        projectSource = amperExternalProject("swiftonlytodo"),
        bundleIdentifier = "swiftonlytodo",
    )

    @Test
    fun kotlinConf2025() = testRunnerStandalone(
        projectSource = ProjectSource.RemoteRepository(
            cloneUrl = "https://github.com/Jeffset/kotlinconf-app.git",
            cloneIntoDirName = "kotlinconf-app",
            refLikeToCheckout = "amper",
        ),
        bundleIdentifier = "com.kotlinconf.iosapp",
        iosAppModuleName = "iosApp",
    )

}
