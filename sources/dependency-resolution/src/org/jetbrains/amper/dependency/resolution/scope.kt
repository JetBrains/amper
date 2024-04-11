/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.dependency.resolution

import org.jetbrains.amper.dependency.resolution.metadata.json.module.Variant
import org.jetbrains.amper.dependency.resolution.metadata.xml.Dependency

/**
 * Represent an Amper dependency resolution scope.
 * It contains rules of how it matches a Gradle usage and a Maven scope.
 */
enum class ResolutionScope(
    private val variantMatcher: (Variant) -> Boolean,
    private val dependencyMatcher: (Dependency) -> Boolean,
) {

    COMPILE(
        { it.attributes["org.gradle.usage"]?.endsWith("-api") == true || it.isMetadataApiElements() },
        { it.scope in setOf(null, "compile") },
    ),
    RUNTIME(
        { it.attributes["org.gradle.usage"]?.endsWith("-runtime") == true || it.isMetadataApiElements() },
        { it.scope in setOf(null, "compile", "runtime") },
    );

    fun matches(variant: Variant) = variantMatcher(variant)
    fun matches(dependency: Dependency) = dependencyMatcher(dependency)
}

internal fun Variant.isMetadataApiElements() =
    attributes["org.jetbrains.kotlin.platform.type"] == PlatformType.COMMON.value
            && attributes["org.gradle.usage"] == "kotlin-metadata"
