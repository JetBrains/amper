/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.tree.reading.maven

import com.intellij.psi.PsiElement
import org.jetbrains.amper.frontend.SchemaBundle
import org.jetbrains.annotations.Nls

class MavenCoordinatesHaveSpace(
    override val element: PsiElement,
    override val coordinates: String,
) : MavenCoordinatesParsingProblem() {
    companion object {
        const val ID = "maven.coordinates.have.space"
    }

    override val buildProblemId get() = ID
    override val message: @Nls String = SchemaBundle.message(ID)

    val spaceIndices = coordinates.indices.filter { coordinates[it] == ' ' }

    override val details: @Nls String = buildString {
        appendLine(coordinates)
        var lastIndex = 0
        for (index in spaceIndices) {
            append(" ".repeat(index - lastIndex))
            append("^")
            lastIndex = index + 1
        }
    }
}