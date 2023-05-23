package org.jetbrains.deft.proto.gradle

import org.jetbrains.deft.proto.gradle.util.TestBase
import org.jetbrains.deft.proto.gradle.util.doTest
import org.junit.jupiter.api.Test


class SimpleModelsTests : TestBase() {

    @Test
    fun commonFragmentTest() = doTest(Models.commonFragmentModel)

    @Test
    fun twoFragmentJvmTest() =doTest(Models.jvmTwoFragmentModel)

    @Test
    fun threeFragmentsSingleArtifactModel() = doTest(Models.threeFragmentsSingleArtifactModel)
}