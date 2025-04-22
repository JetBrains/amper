/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.dependency.resolution.diagnostics

import org.jetbrains.amper.dependency.resolution.DependencyResolutionBundle
import org.jetbrains.amper.dependency.resolution.MavenCoordinates
import org.jetbrains.annotations.Nls

data class MetadataResolvedWithPomErrors(
    val dependency: MavenCoordinates,
    override val childMessages: List<Message>,
) : WithChildMessages {
    companion object {
        const val ID = "metadata.resolved.with.pom.errors"
    }

    override val id: String = ID
    override val severity: Severity = Severity.WARNING
    override val message: @Nls String = DependencyResolutionBundle.message(id, dependency)
}
