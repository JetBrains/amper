/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */


import com.intellij.psi.PsiElement
import org.jetbrains.amper.frontend.SchemaBundle
import org.jetbrains.annotations.Nls

/**
 * Coordinates are used for building paths to the artifacts in the Amper local storage, slashes will affect the path.
 */
class MavenCoordinatesHaveSlash(
    override val element: PsiElement,
    override val coordinates: String,
) : MavenCoordinatesParsingProblem() {
    companion object {
        const val ID = "maven.coordinates.have.slash"
    }

    override val buildProblemId get() = ID
    override val message: @Nls String = SchemaBundle.message(ID)

    val slashIndices = coordinates.indices.filter { coordinates[it] == '\\' || coordinates[it] == '/' }

    override val details: @Nls String = buildString {
        appendLine(coordinates)
        var lastIndex = 0
        for (index in slashIndices) {
            append(" ".repeat(index - lastIndex))
            append("^")
            lastIndex = index + 1
        }
    }
}
