/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.dependency.resolution.metadata.json

import org.jetbrains.amper.dependency.resolution.nameToDependency
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.TestInfo
import java.nio.file.Path
import kotlin.io.path.readText

abstract class JsonTestBase<T> {

    abstract fun getTestDataPath(name: String): Path
    abstract fun String.parse(): T
    abstract fun serialize(model: T): String

    protected fun doTest(testInfo: TestInfo, sanitizer: (String) -> String = { it }) {
        val text = getTestDataText(testInfo.nameToDependency())
        val model = text.parse()
        assertEquals(sanitizer(sanitize(text)), sanitizer(serialize(model)))
    }

    protected fun getTestDataText(name: String) = getTestDataPath(name).readText()

    private fun sanitize(text: String) = text.replace("\\s+".toRegex(), "")
}
