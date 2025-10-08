/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.tree.reading.maven

import com.intellij.psi.PsiElement
import org.jetbrains.amper.core.UsedInIdePlugin
import org.jetbrains.amper.frontend.SchemaBundle
import org.jetbrains.annotations.Nls

class MavenCoordinatesHaveTooFewParts(
    override val element: PsiElement,
    override val coordinates: String,
    @field:UsedInIdePlugin
    val partsSize: Int,
) : MavenCoordinatesParsingProblem() {
    companion object {
        const val ID = "maven.coordinates.have.too.few.parts"
    }

    override val buildProblemId get() = ID
    override val message: @Nls String = SchemaBundle.message(ID, coordinates, partsSize)
}