/*
 * Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.schema.helper

import java.nio.file.Path
import kotlin.io.path.absolutePathString
import kotlin.io.path.name
import kotlin.io.path.writeText
import kotlin.test.asserter

fun assertEqualsIgnoreLineSeparator(expectedContent: String, actualContent: String, originalFile: Path) {
    if (expectedContent.replaceLineSeparators() != actualContent.replaceLineSeparators()) {
        originalFile.parent.resolve(originalFile.name + ".tmp").writeText(actualContent)
        asserter.assertEquals("Comparison failed, original file: ${originalFile.absolutePathString()}", expectedContent, actualContent)
    }
}

fun assertEqualsIgnoreLineSeparator(expected: String, checkText: String, message: String? = null) {
    if (expected.replaceLineSeparators() != checkText.replaceLineSeparators()) {
        asserter.assertEquals(message, expected, checkText)
    }
}

fun String.replaceLineSeparators() = replace("\n", "").replace("\r", "")