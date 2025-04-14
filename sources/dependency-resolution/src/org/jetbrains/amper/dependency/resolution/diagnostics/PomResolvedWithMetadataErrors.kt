/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.dependency.resolution.diagnostics

import org.jetbrains.amper.dependency.resolution.DependencyResolutionBundle
import org.jetbrains.amper.dependency.resolution.MavenCoordinates

data class PomResolvedWithMetadataErrors(
    val dependency: MavenCoordinates,
    override val childMessages: List<Message>,
) : WithChildMessages {
    override val id: String = "pom.resolved.with.metadata.errors"
    override val severity: Severity = Severity.WARNING
    override val message: String = DependencyResolutionBundle.message(id, dependency)
}
