package org.jetbrains.amper.gradle

import org.jetbrains.amper.gradle.util.TestBase
import org.jetbrains.amper.gradle.util.doTest
import org.junit.jupiter.api.Test


class MultipleModulesTests : TestBase() {

    @Test
    fun twoModulesTest() = doTest(Models.twoModulesModel)

    @Test
    fun twoModulesTwoFragmentsTest() = doTest(Models.twoModulesTwoFragmentsModel)
}