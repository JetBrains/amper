/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.dependency.resolution.diagnostics

import kotlinx.serialization.Serializable
import org.jetbrains.amper.dependency.resolution.DependencyResolutionBundle
import org.jetbrains.amper.dependency.resolution.MavenCoordinates
import org.jetbrains.amper.dependency.resolution.Repository
import org.jetbrains.amper.dependency.resolution.ResolutionLevel
import org.jetbrains.annotations.Nls

@Serializable
class UnableToResolveDependency(
    val coordinates: MavenCoordinates,
    val repositories: List<Repository>,
    private val resolutionLevel: ResolutionLevel,
    override val childMessages: List<Message>,
) : WithChildMessages {
    companion object {
        const val ID = "unable.to.resolve.dependency"
    }

    override val id: String = ID

    override val message: @Nls String = DependencyResolutionBundle.message(id, coordinates)

    override val severity: Severity =
        if (resolutionLevel == ResolutionLevel.NETWORK) Severity.ERROR else Severity.WARNING

    override val details: @Nls String
        get() = buildString {
            super.details?.let(::appendLine)
            appendLine(DependencyResolutionBundle.message("unable.to.resolve.dependency.repositories.header"))
            append(repositories.joinToString(separator = "\n") { "  - $it" })
        }
}
