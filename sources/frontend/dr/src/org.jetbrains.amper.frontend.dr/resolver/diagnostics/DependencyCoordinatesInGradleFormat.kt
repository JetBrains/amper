/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.dr.resolver.diagnostics

import org.jetbrains.amper.core.UsedInIdePlugin
import org.jetbrains.amper.dependency.resolution.diagnostics.Message
import org.jetbrains.amper.dependency.resolution.diagnostics.Severity
import org.jetbrains.amper.frontend.dr.resolver.FrontendDrBundle
import org.jetbrains.amper.frontend.dr.resolver.GradleScope

class DependencyCoordinatesInGradleFormat(
    val coordinates: String,
    @field:UsedInIdePlugin
    val gradleScope: GradleScope,
    @field:UsedInIdePlugin
    val trimmedCoordinates: String,
) : Message {
    override val id: String = "dependency.coordinates.in.gradle.format"
    override val severity: Severity = Severity.ERROR
    override val message: String = FrontendDrBundle.message(id, coordinates)
}
