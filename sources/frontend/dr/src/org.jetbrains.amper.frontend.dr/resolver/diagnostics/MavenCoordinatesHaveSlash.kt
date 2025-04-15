/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.dr.resolver.diagnostics

import org.jetbrains.amper.dependency.resolution.diagnostics.Message
import org.jetbrains.amper.dependency.resolution.diagnostics.Severity
import org.jetbrains.amper.frontend.dr.resolver.FrontendDrBundle

/**
 * Coordinates are used for building paths to the artifacts in the Amper local storage, slashes will affect the path.
 */
class MavenCoordinatesHaveSlash(val coordinates: String) : Message {
    override val id: String = "maven.coordinates.have.slash"
    override val severity: Severity = Severity.ERROR
    override val message: String = FrontendDrBundle.message(id)

    override val detailedMessage: String
        get() = "$message\n$details"

    val slashIndices = coordinates.indices.filter { coordinates[it] == '\\' || coordinates[it] == '/' }

    val details = buildString {
        appendLine(coordinates)
        var lastIndex = 0
        for (index in slashIndices) {
            append(" ".repeat(index - lastIndex))
            append("^")
            lastIndex = index + 1
        }
    }
}
