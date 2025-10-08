/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.tree.reading.maven

import com.intellij.psi.PsiElement
import org.jetbrains.amper.core.UsedInIdePlugin
import org.jetbrains.amper.frontend.SchemaBundle
import org.jetbrains.amper.problems.reporting.Level
import org.jetbrains.annotations.Nls

class MavenClassifiersAreNotSupported(
    override val element: PsiElement,
    override val coordinates: String,
    val classifier: String,
) : MavenCoordinatesParsingProblem(level = Level.Warning) {
    companion object {
        const val ID = "maven.classifiers.are.not.supported"
    }

    override val buildProblemId get() = ID
    override val message: @Nls String = SchemaBundle.message(ID, coordinates, classifier)

    @UsedInIdePlugin
    val classifierCanBeShorthand: Boolean = when (classifier) {
        "compile-only",
        "runtime-only",
        "exported",
            -> true

        else -> false
    }

    override val details: @Nls String = buildString {
        append(SchemaBundle.message("maven.classifiers.are.not.supported.details", coordinates, classifier))
        if (classifierCanBeShorthand) {
            append(" ")
            append(SchemaBundle.message("maven.classifiers.are.not.supported.details.shorthand", classifier))
        }
    }
}