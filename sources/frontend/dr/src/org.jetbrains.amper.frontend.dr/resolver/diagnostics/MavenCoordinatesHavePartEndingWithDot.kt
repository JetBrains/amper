/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.dr.resolver.diagnostics

import org.jetbrains.amper.dependency.resolution.diagnostics.Message
import org.jetbrains.amper.dependency.resolution.diagnostics.Severity
import org.jetbrains.amper.frontend.dr.resolver.FrontendDrBundle
import org.jetbrains.annotations.Nls

/**
 * Coordinates are used for building paths to the artifacts in the Amper local storage.
 * Windows strips the trailing dots while creating directories, this way path to artifacts of dependencies with
 * coordinates `A:B:v1` and `A...:B.:v1..` are indistinguishable.
 *
 * See [Windows documentation](https://learn.microsoft.com/en-us/windows/win32/fileio/naming-a-file)
 */
class MavenCoordinatesHavePartEndingWithDot(val coordinates: String) : Message {
    override val id: String = "maven.coordinates.have.part.ending.with.dot"
    override val severity: Severity = Severity.ERROR
    override val message: @Nls String = FrontendDrBundle.message(id)

    val partsEndingWithDot = coordinates.split(':').filter { it.endsWith('.') }

    override val details: @Nls String = buildString {
        appendLine(coordinates)
        var current = 0
        for (part in partsEndingWithDot) {
            val partStart = coordinates.indexOf(part)

            append(" ".repeat(partStart - current))
            append("^".repeat(part.length))

            current = partStart + part.length
        }
    }
}
