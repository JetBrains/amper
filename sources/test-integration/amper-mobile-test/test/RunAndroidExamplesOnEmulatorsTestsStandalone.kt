/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */


import androidUtils.AndroidBaseTest
import org.junit.jupiter.api.Test

class RunAndroidExamplesOnEmulatorsTestsStandalone : AndroidBaseTest() {

    @Test
    fun simple() = runInstrumentedTests(
        projectSource = ProjectSource.Local("simple"),
    )

    @Test
    fun appcompat() = runInstrumentedTests(
        projectSource = ProjectSource.Local("appcompat"),
    )

    @Test
    fun parcelize() = runInstrumentedTests(
        projectSource = ProjectSource.Local("parcelize"),
    )
}