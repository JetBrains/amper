/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.dependency.resolution.diagnostics

import org.jetbrains.amper.dependency.resolution.DependencyResolutionBundle
import org.jetbrains.amper.dependency.resolution.MavenDependency
import org.jetbrains.amper.dependency.resolution.Repository
import org.jetbrains.amper.dependency.resolution.ResolutionLevel
import org.jetbrains.annotations.Nls

class UnableToResolveDependency(
    val dependency: MavenDependency,
    val repositories: List<Repository>,
    resolutionLevel: ResolutionLevel,
    override val childMessages: List<Message>,
) : WithChildMessages {
    override val id: String = "unable.to.resolve.dependency"

    override val message: @Nls String = DependencyResolutionBundle.message(id, dependency)

    override val severity: Severity =
        if (resolutionLevel == ResolutionLevel.NETWORK) Severity.ERROR else Severity.WARNING

    override val details: @Nls String?
        get() = buildString {
            appendLine(super.details)
            appendLine(DependencyResolutionBundle.message("unable.to.resolve.dependency.repositories.header"))
            append(repositories.joinToString(separator = "\n") { "  - ${it.url}" })
        }
}
