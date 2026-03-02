/*
 * Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.tree.reading.maven

import com.intellij.psi.PsiElement
import org.jetbrains.amper.core.UsedInIdePlugin
import org.jetbrains.amper.frontend.SchemaBundle
import org.jetbrains.amper.frontend.tree.TreeDiagnosticId
import org.jetbrains.amper.problems.reporting.DiagnosticId
import org.jetbrains.annotations.Nls

class DependencyCoordinatesInGradleFormat(
    override val element: PsiElement,
    override val coordinates: String,
    @field:UsedInIdePlugin
    val gradleScope: GradleScope,
    @field:UsedInIdePlugin
    val trimmedCoordinates: String,
) : MavenCoordinatesParsingProblem() {
    companion object {
        const val ID = "dependency.coordinates.in.gradle.format"
    }

    @Deprecated("Should be replaced with `diagnosticId` property", replaceWith = ReplaceWith("diagnosticId"))
    override val buildProblemId get() = ID
    override val diagnosticId: DiagnosticId = TreeDiagnosticId.CoordinatesInGradleFormat
    override val message: @Nls String = SchemaBundle.message(ID, coordinates)
}