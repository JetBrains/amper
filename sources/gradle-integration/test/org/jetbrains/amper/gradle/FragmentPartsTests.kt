/*
 * Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.gradle


import org.jetbrains.amper.gradle.util.TestBase
import org.jetbrains.amper.gradle.util.doTest
import org.junit.jupiter.api.Test


class FragmentPartsTests : TestBase() {

    // TODO Investigate, why InlineClasses are not passed to jvm source set.
    @Test
    fun kotlinFragmentPartTest() = doTest(Models.kotlinPartModel)
}