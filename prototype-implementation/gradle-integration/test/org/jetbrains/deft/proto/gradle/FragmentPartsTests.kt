package org.jetbrains.deft.proto.gradle


import org.jetbrains.deft.proto.gradle.util.TestBase
import org.jetbrains.deft.proto.gradle.util.doTest
import org.junit.jupiter.api.Test


class FragmentPartsTests : TestBase() {

    // TODO Investigate, why InlineClasses are not passed to jvm source set.
    @Test
    fun kotlinFragmentPartTest() = doTest(Models.kotlinPartModel)
}