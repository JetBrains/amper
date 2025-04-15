/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.dr.resolver.diagnostics

import org.jetbrains.amper.dependency.resolution.diagnostics.Message
import org.jetbrains.amper.dependency.resolution.diagnostics.Severity
import org.jetbrains.amper.frontend.dr.resolver.FrontendDrBundle

class MavenCoordinatesHaveTooManyParts(
    val coordinates: String,
    partsSize: Int,
) : Message {
    override val id: String = "maven.coordinates.have.too.many.parts"
    override val severity: Severity = Severity.ERROR
    override val message: String = FrontendDrBundle.message(id, coordinates, partsSize)
}
