/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.dr.resolver.diagnostics

import org.jetbrains.amper.core.UsedInIdePlugin
import org.jetbrains.amper.dependency.resolution.diagnostics.Message
import org.jetbrains.amper.dependency.resolution.diagnostics.Severity
import org.jetbrains.amper.frontend.dr.resolver.FrontendDrBundle

class MavenClassifiersAreNotSupported(
    val coordinates: String,
    val classifier: String,
) : Message {
    override val id: String = "maven.classifiers.are.not.supported"
    override val severity: Severity = Severity.WARNING
    override val message: String = FrontendDrBundle.message(id, coordinates, classifier)

    @UsedInIdePlugin
    val classifierCanBeShorthand: Boolean = when (classifier) {
        "compile-only",
        "runtime-only",
        "exported",
            -> true

        else -> false
    }

    override val detailedMessage: String
        get() = "$message\n$details"

    val details = buildString {
        append(FrontendDrBundle.message("maven.classifiers.are.not.supported.details", coordinates, classifier))
        if (classifierCanBeShorthand) {
            append(" ")
            append(FrontendDrBundle.message("maven.classifiers.are.not.supported.details.shorthand", classifier))
        }
    }
}
