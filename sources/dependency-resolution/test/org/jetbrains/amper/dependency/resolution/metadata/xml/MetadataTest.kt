/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.dependency.resolution.metadata.xml

import org.jetbrains.amper.dependency.resolution.nameToDependency
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInfo
import kotlin.io.path.Path
import kotlin.io.path.readText

class MetadataTest {

    @Test
    fun `packagesearch-plugin-gradle-core-shadow-233_13000-SNAPSHOT`(testInfo: TestInfo) = doTest(testInfo)

    @Test
    fun `symbol-processing-api-2_0_20-1_0_26-SNAPSHOT`(testInfo: TestInfo) = doTest(testInfo)

    private fun doTest(testInfo: TestInfo) {
        val text = Path("testData/metadata/xml/metadata/${testInfo.nameToDependency()}.xml").readText()
        val metadata = text.parseMetadata()
        assertEquals(sanitize(text), metadata.serialize())
    }

    private fun sanitize(text: String) =
        // non-greedy (*?) match any symbol ([\S\s]) including new lines
        text.replace("<!--[\\S\\s]*?-->".toRegex(), "")
            .replace(">\\s+".toRegex(), ">")
            .replace("\"\\s+".toRegex(), "\"")
            .replace("\\s+".toRegex(), " ")
            .replace("""<\?xml [\S\s]*?\?>""".toRegex(), "")
}
