package org.jetbrains.amper.gradle


import org.jetbrains.amper.gradle.util.TestBase
import org.jetbrains.amper.gradle.util.doTest
import org.junit.jupiter.api.Test


class FragmentPartsTests : TestBase() {

    // TODO Investigate, why InlineClasses are not passed to jvm source set.
    @Test
    fun kotlinFragmentPartTest() = doTest(Models.kotlinPartModel)
}