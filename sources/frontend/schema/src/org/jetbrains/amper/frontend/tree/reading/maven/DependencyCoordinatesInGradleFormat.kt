/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.tree.reading.maven

import com.intellij.psi.PsiElement
import org.jetbrains.amper.core.UsedInIdePlugin
import org.jetbrains.amper.frontend.SchemaBundle
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

    override val buildProblemId get() = ID
    override val message: @Nls String = SchemaBundle.message(ID, coordinates)
}