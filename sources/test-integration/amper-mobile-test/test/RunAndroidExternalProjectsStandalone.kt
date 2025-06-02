/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */


import androidUtils.AndroidBaseTest
import org.junit.jupiter.api.Test

class RunAndroidExternalProjectsStandalone : AndroidBaseTest() {

    @Test
    fun kmptexterAppTest() = runInstrumentedTests(
        projectSource = amperExternalProject("kmptxter"),
        applicationId = "com.river.kmptxter",
    )

    @Test
    fun kotlinConfAppTest() = runInstrumentedTests(
        projectSource = amperExternalProject("kotlinconf"),
        applicationId = "com.jetbrains.kotlinconf.android",
        androidAppModuleName = "android-app",
    )

    @Test
    fun toDoListApp() = runInstrumentedTests(
        projectSource = amperExternalProject("todolistlite"),
        applicationId = "org.jetbrains.todo",
        androidAppModuleName = "android-app",
    )

    @Test
    fun recipeApp() = runInstrumentedTests(
        projectSource = amperExternalProject("recipeapp"),
        applicationId = "com.recipeapp",
        androidAppModuleName = "android-app",
    )

    @Test
    fun kotlinConfAppTest2025() = runInstrumentedTests(
        projectSource = ProjectSource.RemoteRepository(
            cloneUrl = "https://github.com/Jeffset/kotlinconf-app.git",
            cloneIntoDirName = "kotlinconf-app",
            refLikeToCheckout = "amper",
        ),
        applicationId = "com.jetbrains.kotlinconf",
        androidAppModuleName = "androidApp",
    )
}