/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.dependency.resolution.diagnostics

import org.jetbrains.amper.dependency.resolution.DependencyResolutionBundle
import org.jetbrains.amper.dependency.resolution.MavenDependency
import org.jetbrains.annotations.Nls

class BomDeclaredAsRegularDependency(val dependency: MavenDependency) : Message {
    companion object {
        const val ID = "bom.declared.as.regular.dependency"
    }

    override val id: String = ID
    override val severity: Severity = Severity.ERROR
    override val message: @Nls String = DependencyResolutionBundle.message(id, dependency)
}
