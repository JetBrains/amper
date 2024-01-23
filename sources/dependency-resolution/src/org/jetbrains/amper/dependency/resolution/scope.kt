/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.dependency.resolution

import org.jetbrains.amper.dependency.resolution.metadata.json.Variant
import org.jetbrains.amper.dependency.resolution.metadata.xml.Dependency

enum class Scope(
    private val variantMatcher: (Variant) -> Boolean,
    private val dependencyMatcher: (Dependency) -> Boolean,
) {

    COMPILE(
        { it.attributes["org.gradle.usage"]?.endsWith("-api") == true },
        { it.scope in setOf(null, "compile") },
    ),
    RUNTIME(
        { it.attributes["org.gradle.usage"]?.endsWith("-runtime") == true },
        { it.scope in setOf(null, "compile", "runtime") },
    );

    fun matches(variant: Variant) = variantMatcher(variant)
    fun matches(dependency: Dependency) = dependencyMatcher(dependency)
}
