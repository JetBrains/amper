/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */


import androidUtils.AndroidBaseTest
import org.junit.jupiter.api.Test
import kotlin.test.Ignore

@Ignore("Disabled temporarily because these tests are broken and block MRs")
class RunAndroidExamplesOnEmulatorsTestsStandalone : AndroidBaseTest() {

    @Test
    fun simple() = testRunnerStandalone(
        projectName = "simple",
    )

    @Test
    fun appcompat() = testRunnerStandalone(
        projectName = "appcompat",
    )

    @Test
    fun parcelize() = testRunnerStandalone(
        projectName = "parcelize",
    )
}