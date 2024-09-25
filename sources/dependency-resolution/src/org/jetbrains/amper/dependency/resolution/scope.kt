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
    val fallback: () -> ResolutionScope? = { null }
) {

    COMPILE(
        { it.attributes["org.gradle.usage"]?.endsWith("-api") == true || it.isScopeAgnostic() },
        { it.scope in setOf(null, "compile") },
        // this fallback is naturally expected in Gradle architecture
        // (but might, perhaps, have some questionable implications for Maven dependencies resolved from pom)
        { RUNTIME }
    ),
    RUNTIME(
        { it.attributes["org.gradle.usage"]?.endsWith("-runtime") == true || it.isScopeAgnostic() },
        { it.scope in setOf(null, "compile", "runtime") },
        // 'org.gradle.usage' equal to 'kotlin-runtime' is somewhat artificial for Gradle module metadata,
        // 'kotlin-runtime' value is never resolved in the wild for anything but sources, thus fallback to COMPILE is
        // in fact what is expected
        // (again it might, perhaps, have some questionable implications for Maven dependencies resolved from pom)
        { COMPILE }
    );

    internal fun matches(variant: Variant) = variantMatcher(variant)
    internal fun matches(dependency: Dependency) = dependencyMatcher(dependency)
}

internal fun Variant.isKotlinMetadata(platform: ResolutionPlatform = ResolutionPlatform.COMMON) =
    attributes["org.jetbrains.kotlin.platform.type"] == platform.type.value
            && attributes["org.gradle.usage"] == "kotlin-metadata"

internal fun Variant.isKotlinMetadataSources(platform: ResolutionPlatform = ResolutionPlatform.COMMON) =
    attributes["org.jetbrains.kotlin.platform.type"] == platform.type.value
            && attributes["org.gradle.category"] == "documentation"

private fun Variant.isDocumentation() = attributes["org.gradle.category"] == "documentation"

private fun Variant.isScopeAgnostic() = isKotlinMetadata() || isDocumentation()
