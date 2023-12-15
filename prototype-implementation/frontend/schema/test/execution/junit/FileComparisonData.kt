/*
 * Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package com.intellij.rt.execution.junit

interface FileComparisonData {
    val actual: Any?
    val actualStringPresentation: String?
    val expected: Any?
    val expectedStringPresentation: String?
    val filePath: String?
    val actualFilePath: String?
}
