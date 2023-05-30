package org.jetbrains.deft.proto.gradle

import org.jetbrains.deft.proto.gradle.util.TestBase
import org.jetbrains.deft.proto.gradle.util.doTest
import org.junit.jupiter.api.Test


class AndroidArtifactTests : TestBase() {

    @Test
    fun kotlinFragmentPartTest() = doTest(Models.singleFragmentAndroidModel)

}