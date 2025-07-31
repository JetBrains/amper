/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.dependency.resolution.diagnostics

import kotlinx.serialization.Serializable
import org.jetbrains.amper.dependency.resolution.DependencyResolutionBundle
import org.jetbrains.amper.dependency.resolution.MavenCoordinates
import org.jetbrains.amper.dependency.resolution.MavenDependency
import org.jetbrains.amper.dependency.resolution.ResolutionPlatform
import org.jetbrains.annotations.Nls

@Serializable
class PlatformsAreNotSupported private constructor(
    val coordinates: MavenCoordinates,
    val requestedPlatforms: Set<ResolutionPlatform>,
    val supportedPlatforms: Set<ResolutionPlatform>,
) : Message {

    constructor(dependency: MavenDependency, supportedPlatforms: Set<ResolutionPlatform>)
            : this(dependency.coordinates, dependency.resolutionConfig.platforms, supportedPlatforms)

    companion object {
        const val ID = "platforms.are.not.supported"
    }

    override val id: String = ID
    override val severity: Severity = Severity.ERROR
    override val message: @Nls String =
        DependencyResolutionBundle.message(
            id,
            unsupportedPlatforms.size,
            unsupportedPlatforms.map(ResolutionPlatform::pretty).sorted().joinToString(),
            coordinates,
        )

    override val details: @Nls String
        get() = buildString {
            append(
                DependencyResolutionBundle.message(
                    "platforms.are.not.supported.details",
                    supportedPlatforms.size,
                    supportedPlatforms.map(ResolutionPlatform::pretty).sorted().joinToString(),
                )
            )
        }

    val unsupportedPlatforms: Set<ResolutionPlatform>
        get() = requestedPlatforms - supportedPlatforms

    override val reportTransitive: Boolean = false
}
