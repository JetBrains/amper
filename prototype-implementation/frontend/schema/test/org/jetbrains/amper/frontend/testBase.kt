/*
 * Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend

import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.test.assertEquals


/**
 * Base test class, that derives standard and input paths from case name.
 */
abstract class FileExpectTest(
    private val expectPostfix: String,
    private val inputPostfix: String = ".yaml",
    private val forceInput: Boolean = true,
) {

    fun test(caseName: String) {
        val inputFileName = "testResources/$caseName$inputPostfix"
        val input = Path(inputFileName)
        if (forceInput && !input.exists()) error("No input file: $inputFileName")

        val expectFileName = "testResources/$caseName$expectPostfix"
        val expect = Path(expectFileName)
        if (!expect.exists()) error("No expect file: $expectFileName")
        val expectContent = expect.readText()

        val actualContent = getActualContent(input)

        // expected data on disk may have a different line endings, you may never know
        // for comparing text data '\r' never matters anyway
        assertEquals(expectContent.replace("\r", ""), actualContent.replace("\r", ""))
    }

    abstract fun getActualContent(input: Path): String
}