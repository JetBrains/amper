package com.intellij.rt.execution.junit

interface FileComparisonData {
    val actual: Any?
    val actualStringPresentation: String?
    val expected: Any?
    val expectedStringPresentation: String?
    val filePath: String?
    val actualFilePath: String?
}
