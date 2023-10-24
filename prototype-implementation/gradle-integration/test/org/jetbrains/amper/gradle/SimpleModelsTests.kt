package org.jetbrains.amper.gradle

import org.jetbrains.amper.gradle.util.TestBase
import org.jetbrains.amper.gradle.util.doTest
import org.junit.jupiter.api.Test


class SimpleModelsTests : TestBase() {

    @Test
    fun commonFragmentTest() = doTest(Models.commonFragmentModel)

    @Test
    fun twoFragmentJvmTest() =doTest(Models.jvmTwoFragmentModel)

    @Test
    fun threeFragmentsSingleArtifactModel() = doTest(Models.threeFragmentsSingleArtifactModel)
}