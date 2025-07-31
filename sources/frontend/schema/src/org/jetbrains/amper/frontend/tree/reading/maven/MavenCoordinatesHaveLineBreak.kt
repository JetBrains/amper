/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.tree.reading.maven

import com.intellij.psi.PsiElement
import org.jetbrains.amper.frontend.SchemaBundle
import org.jetbrains.annotations.Nls

class MavenCoordinatesHaveLineBreak(
    override val element: PsiElement,
    override val coordinates: String,
) : MavenCoordinatesParsingProblem() {
    companion object {
        const val ID = "maven.coordinates.have.line.break"
    }

    override val buildProblemId get() = ID
    override val message: @Nls String = SchemaBundle.message(ID)

    val lineBreakIndices = coordinates.indices.filter { coordinates[it] == '\n' || coordinates[it] == '\r' }

    override val details: @Nls String = buildString {
        val sanitizedCoordinates = coordinates
            .replace("\n", "\\n")
            .replace("\r", "\\r")

        appendLine(sanitizedCoordinates)
        var lastIndex = 0
        for (index in lineBreakIndices) {
            append(" ".repeat(index - lastIndex))
            append("^^")
            lastIndex = index + 1
        }
    }
}
