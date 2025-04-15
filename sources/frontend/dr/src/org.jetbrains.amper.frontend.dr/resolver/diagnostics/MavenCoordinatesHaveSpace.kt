/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.dr.resolver.diagnostics

import org.jetbrains.amper.dependency.resolution.diagnostics.Message
import org.jetbrains.amper.dependency.resolution.diagnostics.Severity
import org.jetbrains.amper.frontend.dr.resolver.FrontendDrBundle

class MavenCoordinatesHaveSpace(
    val coordinates: String,
    spaceIndex: Int,
) : Message {
    override val id: String = "maven.coordinates.have.space"
    override val severity: Severity = Severity.ERROR
    override val message: String = FrontendDrBundle.message(id, coordinates, spaceIndex)

    override val detailedMessage: String
        get() = "$message\n$details"

    val spaceIndices = coordinates.indices.filter { coordinates[it] == ' ' }

    val details = buildString {
        appendLine(coordinates)
        var lastIndex = 0
        for (index in spaceIndices) {
            append(" ".repeat(index - lastIndex))
            append("^")
            lastIndex = index + 1
        }
    }
}
