/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.maven

/**
 * The proper quoting according to https://yaml.org/spec/1.2/spec.html is very complex and probably isn't needed here
 *
 * This utility intends to support the very specific use-case when a string starts with a star (*) when configuring
 * maven surefire plugin with a maven compat layer.
 */
internal object YamlQuoting {
    private val startQuoteTriggers = setOf(
        '-', '?', ':', ',', '[', ']', '{', '}', '#', '&', '*', '!', '|', '>',
        '\'', '"', '%', '@', '`'
    )

    private val notFollowedBySpaceExceptions = setOf(
        '-', '?', ':'
    )

    fun requiresQuoting(value: String): Boolean {
        require(value.isNotBlank()) { "Blank strings are not allowed" }
        val c0 = value.first()
        // Excludes `-`, `?`, `:` prefixes if not followed by space
        if (c0 in notFollowedBySpaceExceptions && (value.length == 1 || !value[1].isWhitespace())) return false
        if (c0 in startQuoteTriggers) return true
        if (value.contains(": ")) return true
        if (value.contains(" #")) return true
        return false
    }

    fun quote(value: String): String {
        require(value.isNotBlank()) { "Blank strings are not allowed" }
        if (!requiresQuoting(value)) return value
        return "'${value.replace("'", "''")}'"
    }
}
