/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */


import androidUtils.AndroidBaseTest
import org.jetbrains.amper.test.Dirs
import org.junit.jupiter.api.Test
import kotlin.io.path.div

class RunAndroidExamplesOnEmulatorsTestsStandalone : AndroidBaseTest() {

    private val androidTestProjectsPath = Dirs.amperTestProjectsRoot / "android"

    @Test
    fun simple() = runInstrumentedTests(
        projectSource = ProjectSource.Local(androidTestProjectsPath / "simple"),
    )

    @Test
    fun appcompat() = runInstrumentedTests(
        projectSource = ProjectSource.Local(androidTestProjectsPath / "appcompat"),
    )

    @Test
    fun parcelize() = runInstrumentedTests(
        projectSource = ProjectSource.Local(androidTestProjectsPath / "parcelize"),
    )
}