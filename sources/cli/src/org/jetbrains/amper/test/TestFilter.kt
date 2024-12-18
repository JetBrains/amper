/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.test

import org.slf4j.LoggerFactory

enum class FilterMode {
    Include, Exclude
}

sealed class TestFilter {

    /**
     * A filter that includes a specific test method.
     */
    data class SpecificTestInclude(
        /**
         * The fully qualified name of the class or top-level test suite function containing the test to match.
         * Nested classes are separated from their containing class using the '/' separator.
         */
        val suiteFqn: String,
        /**
         * The simple name of the test method/function to match, excluding the containing class and package.
         */
        val testName: String,
    ): TestFilter()

    /**
     * A filter that includes or excludes a whole test class or top-level test suite function.
     */
    data class SuitePattern(
        /**
         * The fully qualified name of the class or top-level test suite function.
         * Nested classes are separated from their containing class using the '/' separator.
         * May contain '*' to represent any group of characters or '?' to represent a single character.
         *
         * Note: Kotlin identifiers may contain spaces and other symbols like `^$(){}+-=_#%&`.
         * Therefore, they should be properly escaped when used in regexes.
         * `*` and `?` are also technically allowed, but we rely on their special meaning here and they should not be
         * treated literally.
         */
        val pattern: String,
        /**
         * Whether this filter should include or exclude what it matches.
         */
        val mode: FilterMode,
    ): TestFilter()

    companion object {

        private val logger = LoggerFactory.getLogger(TestFilter::class.java)

        // Note: Kotlin identifiers may contain spaces and other symbols like `^$(){}+-=_#%&`.
        // This regex is therefore lenient on purpose.
        private val testFqnRegex = Regex("""(?<suiteFqn>.+)\.(?<method>[^.]+)""")

        fun includeTest(testFqn: String): TestFilter {
            val match = requireNotNull(testFqnRegex.matchEntire(testFqn)) {
                "invalid test name '$testFqn'. Expected a fully qualified method name including the package and class name. " +
                        "Nested classes, if present, should be separated from the containing class using the '/' separator." +
                        "The name should be literal, without wildcards."
            }
            val suiteFqnFilter = match.groups["suiteFqn"]!!.value
            val methodFilter = match.groups["method"]!!.value
            if ("*" in suiteFqnFilter || "*" in methodFilter || "?" in suiteFqnFilter || "?" in methodFilter) {
                logger.warn("When matching a specific test method, '*' and '?' are treated literally.")
            }
            return SpecificTestInclude(suiteFqn = suiteFqnFilter, testName = methodFilter)
        }

        fun includeOrExcludeSuite(pattern: String, mode: FilterMode): TestFilter =
            SuitePattern(pattern = pattern, mode = mode)
    }
}

internal fun String.wildcardsToRegex(): String {
    val wildcardPattern = this
    var prevIndex = 0
    return buildString {
        wildcardPattern.forEachIndexed { index, c ->
            if (c == '*' || c == '?') {
                append(wildcardPattern.literalSubstring(prevIndex until index))
                append(if (c == '*') ".*" else ".")
                prevIndex = index + 1
            }
        }
        append(wildcardPattern.literalSubstring(prevIndex until wildcardPattern.length))
    }
}

private fun String.literalSubstring(range: IntRange) =
    substring(range).let { if (it.isNotEmpty()) Regex.escape(it) else "" }
