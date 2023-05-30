package org.jetbrains.deft.proto.gradle

import org.jetbrains.deft.proto.gradle.util.TestBase
import org.jetbrains.deft.proto.gradle.util.doTest
import org.junit.jupiter.api.Test

class InitProjectsTest : TestBase() {

    @Test
    fun twoDirectoriesProjectPathTest() = doTest(Models.twoDirectoryHierarchyModel)

    @Test
    fun threeDirectoryHierarchyModel() = doTest(Models.threeDirectoryHierarchyModel)

}