/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.dr.resolver.diagnostics

import org.jetbrains.amper.core.UsedInIdePlugin
import org.jetbrains.amper.dependency.resolution.diagnostics.Message
import org.jetbrains.amper.dependency.resolution.diagnostics.Severity
import org.jetbrains.amper.frontend.dr.resolver.FrontendDrBundle
import org.jetbrains.annotations.Nls

class MavenClassifiersAreNotSupported(
    val coordinates: String,
    val classifier: String,
) : Message {
    companion object {
        const val ID = "maven.classifiers.are.not.supported"
    }
    
    override val id: String = ID
    override val severity: Severity = Severity.WARNING
    override val message: @Nls String = FrontendDrBundle.message(id, coordinates, classifier)

    @UsedInIdePlugin
    val classifierCanBeShorthand: Boolean = when (classifier) {
        "compile-only",
        "runtime-only",
        "exported",
            -> true

        else -> false
    }

    override val details: @Nls String = buildString {
        append(FrontendDrBundle.message("maven.classifiers.are.not.supported.details", coordinates, classifier))
        if (classifierCanBeShorthand) {
            append(" ")
            append(FrontendDrBundle.message("maven.classifiers.are.not.supported.details.shorthand", classifier))
        }
    }
}
