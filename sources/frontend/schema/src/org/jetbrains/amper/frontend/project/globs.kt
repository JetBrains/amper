/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.project

import java.nio.file.FileSystems
import java.nio.file.Path
import java.util.regex.PatternSyntaxException

private val globChars = setOf('*', '?', '{', '}', '[', ']', ',')

/**
 * Returns whether this string contains characters that have meaning in glob patterns.
 */
fun String.hasGlobCharacters() = any { it in globChars }

class Glob(pattern: String) {

    // We need to normalize glob paths, otherwise the matcher will miss non-exact matches like "./dir" != "dir".
    // Regular path normalization doesn't work with globs because they contain invalid characters for paths (e.g. '*').
    private val pathMatcher = FileSystems.getDefault().getPathMatcher("glob:${normalize(pattern)}")

    /**
     * Returns whether the given [path] matches this glob.
     */
    fun matches(path: Path): Boolean = pathMatcher.matches(path.normalize())

    companion object {

        /**
         * Validates the given [pattern], and throws [PatternSyntaxException] with details if it's invalid.
         * The pattern is validated as-is, without normalization, so that the potential error matches the input.
         */
        fun checkValid(pattern: String) {
            FileSystems.getDefault().getPathMatcher("glob:$pattern")
        }

        private val regexDot = Regex("""(^|/)\.(/\.)*(/|$)""")
        private val regexDotDot = Regex("""(^|/)(?!\.\./)[^/]+/\.\.(/|$)""")
        private val regexMultipleSlashes = Regex("""/{2,}""")

        /**
         * Normalizes the given glob [pattern].
         *
         * The method performs the following operations:
         * * Replace multiple slashes with a single slash `/`
         * * Remove trailing slashes
         * * Resolve dot segments `./`, `/./`, `/.` by removing them from the path
         * * Resolve `dir/..` segment pairs by removing the pair from the path
         */
        private fun normalize(pattern: String): String {
            // collapse to empty string when the match is at the beginning or end of the path
            fun String.collapsedPathSegment() = if (startsWith("/") && endsWith("/")) "/" else ""

            var cleaned = pattern
                .replace(regexMultipleSlashes, "/")
                .replace(regexDot) { it.value.collapsedPathSegment() }

            while (regexDotDot.find(cleaned) != null) {
                cleaned = cleaned.replace(regexDotDot) { it.value.collapsedPathSegment() }
            }
            if (cleaned != "/") {
                cleaned = cleaned.removeSuffix("/")
            }
            return cleaned
        }
    }
}
