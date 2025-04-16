/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.dr.resolver.diagnostics

import org.jetbrains.amper.dependency.resolution.diagnostics.Message
import org.jetbrains.amper.dependency.resolution.diagnostics.Severity
import org.jetbrains.amper.frontend.dr.resolver.FrontendDrBundle

class MavenCoordinatesHaveLineBreak(val coordinates: String) : Message {
    override val id: String = "maven.coordinates.have.line.break"
    override val severity: Severity = Severity.ERROR
    override val message: String = FrontendDrBundle.message(id)

    val lineBreakIndices = coordinates.indices.filter { coordinates[it] == '\n' || coordinates[it] == '\r' }

    override val details = buildString {
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
