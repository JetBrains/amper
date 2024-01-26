/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.gradle


import org.jetbrains.amper.gradle.util.TestBase
import org.jetbrains.amper.gradle.util.doTest
import org.junit.jupiter.api.Test


class FragmentSettingsTests : TestBase() {

    // TODO Investigate, why InlineClasses are not passed to jvm source set.
    @Test
    fun kotlinFragmentSettingsTest() = doTest(Models.kotlinSettingsModel)
}