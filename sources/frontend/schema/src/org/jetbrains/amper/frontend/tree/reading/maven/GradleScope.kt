/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.tree.reading.maven

import org.jetbrains.amper.core.UsedInIdePlugin

@UsedInIdePlugin
enum class GradleScope {
    api,
    implementation, compile,
    testImplementation, testCompile,
    compileOnly,
    compileOnlyApi,
    testCompileOnly,
    runtimeOnly, runtime,
    testRuntimeOnly, testRuntime;

    companion object {
        fun parseGradleScope(coordinates: String): Pair<GradleScope, String>? =
            entries
                .firstOrNull { coordinates.startsWith("${it.name}(") }
                ?.let { gradleScope ->
                    val gradleScopePrefix = "${gradleScope.name}("
                    val trimmedCoordinates = trimPrefixAndSuffixOrNull(coordinates, "$gradleScopePrefix\"", "\")")
                        ?: trimPrefixAndSuffixOrNull(coordinates, "$gradleScopePrefix'", "')")
                        ?: return@let null
                    gradleScope to trimmedCoordinates
                }

        private fun trimPrefixAndSuffixOrNull(coordinates: String, prefix: String, suffix: String): String? =
            coordinates
                .takeIf { it.startsWith(prefix) && it.endsWith(suffix) }
                ?.substringAfter(prefix)
                ?.substringBefore(suffix)
    }
}