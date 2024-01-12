/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.dependency.resolution.metadata.json

import org.jetbrains.amper.dependency.resolution.nameToDependency
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.TestInfo
import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.test.Test

class JsonTest {

    @Test
    fun `kotlin-stdlib-1_9_20`(testInfo: TestInfo) = doTest(testInfo)

    @Test
    fun `kotlin-test-1_9_20`(testInfo: TestInfo) = doTest(testInfo)

    @Test
    fun `kotlin-test-junit-1_9_20`(testInfo: TestInfo) = doTest(testInfo)

    @Test
    fun `kotlinx-coroutines-core-1_6_4`(testInfo: TestInfo) = doTest(testInfo)

    @Test
    fun `kotlinx-coroutines-core-jvm-1_6_4`(testInfo: TestInfo) = doTest(testInfo)

    @Test
    fun `kotlinx-coroutines-android-1_5_0`(testInfo: TestInfo) = doTest(testInfo) { it.replace("6,", "\"6\",") }

    @Test
    fun `guava-33_0_0-android`(testInfo: TestInfo) = doTest(testInfo) {
        it.replace(
            "ApacheMaven3.9.5(57804ffe001d7215b5e7bcb531cf83df38f93546)",
            "Apache Maven 3.9.5 (57804ffe001d7215b5e7bcb531cf83df38f93546)"
        )
    }

    private fun doTest(testInfo: TestInfo, sanitizer: (String) -> String = { it }) {
        val text = Path.of("testData/metadata/json/${testInfo.nameToDependency()}.module").readText()
        val module = text.parseMetadata()
        assertEquals(sanitizer(sanitize(text)), sanitizer(module.serialize()))
    }

    private fun sanitize(text: String) = text.replace("\\s+".toRegex(), "")
}
