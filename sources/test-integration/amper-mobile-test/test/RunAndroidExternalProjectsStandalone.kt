/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */


import androidUtils.AndroidBaseTest
import org.junit.jupiter.api.Test

class RunAndroidExternalProjectsStandalone : AndroidBaseTest() {

    @Test
    fun kmptexterAppTest() = testRunnerStandalone(
        projectName = "kmptxter",
        applicationId = "com.river.kmptxter",
    )

    @Test
    fun kotlinConfAppTest() = testRunnerStandalone(
        projectName = "kotlinconf",
        applicationId = "com.jetbrains.kotlinconf.android",
        androidAppModuleName = "android-app",
    )

    @Test
    fun toDoListApp() = testRunnerStandalone(
        projectName = "todolistlite",
        applicationId = "org.jetbrains.todo",
        androidAppModuleName = "android-app",
    )

    @Test
    fun recipeApp() = testRunnerStandalone(
        projectName = "recipeapp",
        applicationId = "com.recipeapp",
        androidAppModuleName = "android-app",
    )
}