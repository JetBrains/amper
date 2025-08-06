/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.diagnostics

import com.intellij.psi.PsiElement
import org.jetbrains.amper.frontend.SchemaBundle
import org.jetbrains.amper.frontend.messages.PsiBuildProblem
import org.jetbrains.amper.problems.reporting.BuildProblemId
import org.jetbrains.amper.problems.reporting.Level
import org.jetbrains.annotations.Nls

/**
 * Reported on unknown property names in any object.
 */
class UnknownProperty(
    val invalidName: String,
    val possibleIntendedNames: List<String>,
    override val element: PsiElement,
) : PsiBuildProblem(Level.Error) {
    companion object {
        const val ID = "unknown.property"
    }

    override val buildProblemId: BuildProblemId = ID
    override val message: @Nls String
        get() = if (possibleIntendedNames.isEmpty()) {
            SchemaBundle.message("unknown.property", invalidName)
        } else {
            SchemaBundle.message(
                "unknown.property.did.you.mean",
                invalidName,
                // repeated ORs are ok: we don't even have misnomer collisions yet, let alone more
                possibleIntendedNames.joinToString(" or ") { "'$it'" },
            )
        }
}
