/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.dr.resolver.diagnostics

import org.jetbrains.amper.core.UsedInIdePlugin
import org.jetbrains.amper.dependency.resolution.diagnostics.Message
import org.jetbrains.amper.dependency.resolution.diagnostics.Severity
import org.jetbrains.amper.frontend.dr.resolver.FrontendDrBundle
import org.jetbrains.amper.frontend.dr.resolver.GradleScope
import org.jetbrains.annotations.Nls

class DependencyCoordinatesInGradleFormat(
    val coordinates: String,
    @field:UsedInIdePlugin
    val gradleScope: GradleScope,
    @field:UsedInIdePlugin
    val trimmedCoordinates: String,
) : Message {
    companion object {
        const val ID = "dependency.coordinates.in.gradle.format"
    }

    override val id: String = ID
    override val severity: Severity = Severity.ERROR
    override val message: @Nls String = FrontendDrBundle.message(id, coordinates)
}
