/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */


import androidUtils.AndroidBaseTest
import org.junit.jupiter.api.Test

class RunAndroidExamplesOnEmulatorsTestsStandalone : AndroidBaseTest() {

    @Test
    fun simple() = testRunnerStandalone(
        projectSource = ProjectSource.Local("simple"),
    )

    @Test
    fun appcompat() = testRunnerStandalone(
        projectSource = ProjectSource.Local("appcompat"),
    )

    @Test
    fun parcelize() = testRunnerStandalone(
        projectSource = ProjectSource.Local("parcelize"),
    )
}