package org.jetbrains.deft.proto.gradle

import org.jetbrains.deft.proto.gradle.util.TestBase
import org.jetbrains.deft.proto.gradle.util.doTest
import org.junit.jupiter.api.Test


class MultipleModulesTests : TestBase() {

    @Test
    fun twoModulesTest() = doTest(Models.twoModulesModel)

    @Test
    fun twoModulesTwoFragmentsTest() = doTest(Models.twoModulesTwoFragmentsModel)
}