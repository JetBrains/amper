/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.diagnostics

import com.intellij.psi.PsiElement
import org.jetbrains.amper.frontend.SchemaBundle
import org.jetbrains.amper.frontend.api.TraceableString
import org.jetbrains.amper.frontend.messages.PsiBuildProblem
import org.jetbrains.amper.frontend.messages.extractPsiElement
import org.jetbrains.amper.problems.reporting.BuildProblemId
import org.jetbrains.amper.problems.reporting.Level
import org.jetbrains.annotations.Nls

class UnresolvedModuleDeclaration(
    val modulePath: TraceableString,
) : PsiBuildProblem(Level.Error) {
    companion object {
        const val ID = "unresolved.module.declaration"
    }

    override val element: PsiElement
        get() = modulePath.extractPsiElement()
    override val buildProblemId: BuildProblemId = ID
    override val message: @Nls String
        get() = SchemaBundle.message(
            messageKey = "project.module.path.0.unresolved",
            modulePath.value,
        )
}