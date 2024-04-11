/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.dependency.resolution.metadata.dr.input

import org.jetbrains.amper.dependency.resolution.nameToDependency
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.TestInfo
import kotlin.test.Test

class DrCompleteDataTest {

    @Test
    fun `complete test`(testInfo: TestInfo) {

    }


//    private fun doTest(testInfo: TestInfo, sanitizer: (String) -> String = { it }) {
//        val text = getTestDataText(testInfo.nameToDependency())
//        val input = text.parseMetadata()
//        Assertions.assertEquals(sanitizer(sanitize(text)), sanitizer(input.serialize()))
//    }
}