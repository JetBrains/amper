/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.dr.resolver.diagnostics

import org.jetbrains.amper.dependency.resolution.diagnostics.Message
import org.jetbrains.amper.dependency.resolution.diagnostics.Severity
import org.jetbrains.amper.frontend.dr.resolver.FrontendDrBundle
import org.jetbrains.annotations.Nls

class MavenCoordinatesHaveSpace(
    val coordinates: String,
    spaceIndex: Int,
) : Message {
    companion object {
        const val ID = "maven.coordinates.have.space"
    }

    override val id: String = ID
    override val severity: Severity = Severity.ERROR
    override val message: @Nls String = FrontendDrBundle.message(id, coordinates, spaceIndex)

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
