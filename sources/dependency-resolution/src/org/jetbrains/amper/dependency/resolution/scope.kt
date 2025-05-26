/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.dependency.resolution

import org.jetbrains.amper.dependency.resolution.metadata.json.module.Variant
import org.jetbrains.amper.dependency.resolution.metadata.xml.Dependency
import org.jetbrains.amper.dependency.resolution.attributes.Usage
import org.jetbrains.amper.dependency.resolution.attributes.getAttributeValue
import org.jetbrains.amper.dependency.resolution.attributes.hasKotlinPlatformType
import org.jetbrains.amper.dependency.resolution.attributes.isDocumentation

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
        variantMatcher = { it.getAttributeValue(Usage)?.isApi() == true || it.isScopeAgnostic() },
        dependencyMatcher = { it.scope in setOf(null, "compile") },
        // this fallback is naturally expected in Gradle architecture
        // (but might, perhaps, have some questionable implications for Maven dependencies resolved from pom)
        fallback = { RUNTIME }
    ),
    RUNTIME(
        variantMatcher = { it.getAttributeValue(Usage)?.isRuntime() == true || it.isScopeAgnostic() },
        dependencyMatcher = { it.scope in setOf(null, "compile", "runtime") },
        // 'org.gradle.usage' equal to 'kotlin-runtime' is somewhat artificial for Gradle module metadata,
        // 'kotlin-runtime' value is never resolved in the wild for anything but sources, thus fallback to COMPILE is
        // in fact what is expected
        // (again it might, perhaps, have some questionable implications for Maven dependencies resolved from pom)
        fallback = { COMPILE }
    ),

    @Deprecated("This resolution scope is a hack intended only to assemble a demoable Compose Hot Reload support")
    DEV(
        variantMatcher = { it.getAttributeValue(Usage)?.isComposeDevJavaRuntime() == true || it.isScopeAgnostic() },
        dependencyMatcher = { it.scope in setOf(null, "compile", "runtime") },
        fallback = { RUNTIME }
    );

    internal fun matches(variant: Variant) = variantMatcher(variant)
    internal fun matches(dependency: Dependency) = dependencyMatcher(dependency)
}

internal fun Variant.isKotlinMetadata(platform: ResolutionPlatform = ResolutionPlatform.COMMON) =
    hasKotlinPlatformType(platform.type) && getAttributeValue(Usage) == Usage.KotlinMetadata

internal fun Variant.isKotlinMetadataSources(platform: ResolutionPlatform = ResolutionPlatform.COMMON) =
    hasKotlinPlatformType(platform.type) && isDocumentation()

private fun Variant.isScopeAgnostic() = isKotlinMetadata() || isDocumentation()
