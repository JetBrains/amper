/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.dependency.resolution.metadata.dr.input

//import org.jetbrains.amper.dependency.resolution.metadata.json.module.parseMetadata
//import org.jetbrains.amper.dependency.resolution.metadata.json.module.serialize
import org.jetbrains.amper.dependency.resolution.nameToDependency
import org.jetbrains.amper.test.TestUtil
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.TestInfo
import kotlin.io.path.readText
import kotlin.test.Test

class DrInputSerializationTest {

    @Test
    fun `org-gradle_gradle-tooling-api_8_4`(testInfo: TestInfo) = doTest(testInfo)

    private fun doTest(testInfo: TestInfo, sanitizer: (String) -> String = { it }) {
        val text = getTestDataText(testInfo.nameToDependency())
        val input = text.parseMetadata()
        Assertions.assertEquals(sanitizer(sanitize(text)), sanitizer(input.serialize()))
    }

    private fun getTestDataText(name: String) =
        TestUtil.amperSourcesRoot.resolve("dependency-resolution/testData/metadata/dr-input/${name}").readText()

    private fun sanitize(text: String) = text.replace("\\s+".toRegex(), "")
}