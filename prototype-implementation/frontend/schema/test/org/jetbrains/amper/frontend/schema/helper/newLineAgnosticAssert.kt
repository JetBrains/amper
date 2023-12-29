/*
 * Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.schema.helper

import com.intellij.rt.execution.junit5.FileComparisonFailedError
import java.nio.file.Path
import kotlin.io.path.absolutePathString
import kotlin.test.asserter

fun assertEqualsIgnoreLineSeparator(expectedContent: String, actualContent: String, originalFile: Path) {
    // assertEqualsIgnoreLineSeparator(expectedContent,actualContent) - why not assert with precise diff reported
    if (expectedContent.replaceLineSeparators() != actualContent.replaceLineSeparators()) {
        asserter.assertEquals("Comparison failed", expectedContent, actualContent)
//        throw FileComparisonFailedError(
//            "Comparison failed",
//            expectedContent,
//            actualContent,
//            originalFile.absolutePathString()
//        )
    }
}

fun assertEqualsIgnoreLineSeparator(expected: String, checkText: String, message: String? = null) {
    if (expected.replaceLineSeparators() != checkText.replaceLineSeparators()) {
        asserter.assertEquals(message, expected, checkText)
    }
}

fun String.replaceLineSeparators() = replace("\n", "").replace("\r", "")