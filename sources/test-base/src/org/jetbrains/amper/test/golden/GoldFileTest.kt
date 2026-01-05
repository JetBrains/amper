/*
 * Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.test.golden

import org.jetbrains.amper.frontend.schema.DefaultVersions
import org.jetbrains.amper.test.assertEqualsIgnoreLineSeparator
import org.jetbrains.amper.test.normalizeLineSeparators
import java.io.File
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.absolute
import kotlin.io.path.absolutePathString
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText

/**
 * Shortcut for tests that have very simple [inputContent].
 */
fun GoldFileTest(caseName: String, base: Path, inputContent: GoldFileTest.() -> String) =
    object : GoldFileTest(caseName, base) {
        override fun getInputContent() = inputContent()
    }

/**
 * Represents a single test (usually a single test method) that 
 * retrieves expected content only from the specified gold file.
 *
 * Assertion is made by comparing the actual content with the expected one and generating
 * special `.tmp` file with the actual content in case of mismatch.
 *
 * Also, expected content is normalized first and some interpolations are made.
 * See [readNormalizedExpectedSafelyAndInterpolate] for details.
 *
 * @param caseName The name of the test case.
 * @param rawBase Base directory for the gold files.
 */
abstract class GoldFileTest(val caseName: String, rawBase: Path) {
    /**
     * Absolute path to the base directory for the gold files.
     */
    protected val base: Path = rawBase.absolute()

    protected open val expectPostfix: String = ".result.txt"
    protected open val expectedPath: Path get() = base.resolve(caseName + expectPostfix).absolute()

    /**
     * Get the actual content to be tested.
     */
    abstract fun getInputContent(): String

    fun getExpectContent(): String = expectedPath.readNormalizedExpectedSafelyAndInterpolate()

    /**
     * The order of operations is the following:
     * 1. Read the contents of the [expectedPath], creating the file if it is non-existent.
     * 2. Normalize line separators.
     * 3. Trim trailing whitespaces and empty lines.
     * 4. Replace various `{{ smth }}` placeholders with the actual values.
     * 5. Return the result.
     */
    private fun Path.readNormalizedExpectedSafelyAndInterpolate(): String {
        val testProcessDir = Path(".").normalize().absolutePathString()
        val testResources = Path(".").resolve(base).normalize().absolutePathString()

        // This is the actual check.
        if (!exists()) writeText("")
        return readText()
            .normalizeLineSeparators()
            .trimTrailingWhitespacesAndEmptyLines()
            .replace("{{ testProcessDir }}", testProcessDir)
            .replace("{{ testResources }}", testResources)
            .replace("{{ fileSeparator }}", File.separator)
            .replace("#kotlinVersion", DefaultVersions.kotlin)
            .replace("#composeDefaultVersion", DefaultVersions.compose)
    }

    open fun doTest() {
        val inputContent = getInputContent()
        val expectContent = getExpectContent()
        return assertEqualsIgnoreLineSeparator(
            expectContent,
            inputContent,
            expectedPath,
        )
    }
}
