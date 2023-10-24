package org.jetbrains.amper.gradle

import org.jetbrains.amper.gradle.util.TestBase
import org.jetbrains.amper.gradle.util.doTest
import org.junit.jupiter.api.Test


class AndroidArtifactTests : TestBase() {

    @Test
    fun kotlinFragmentPartTest() = doTest(Models.singleFragmentAndroidModel)

}