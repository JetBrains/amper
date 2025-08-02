/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.diagnostics

import com.intellij.psi.PsiElement
import org.jetbrains.amper.frontend.SchemaBundle
import org.jetbrains.amper.frontend.api.TraceablePath
import org.jetbrains.amper.frontend.messages.PsiBuildProblem
import org.jetbrains.amper.frontend.messages.extractPsiElement
import org.jetbrains.amper.problems.reporting.BuildProblemId
import org.jetbrains.amper.problems.reporting.Level
import org.jetbrains.annotations.Nls
import java.nio.file.Path
import kotlin.io.path.relativeTo

class UnresolvedTemplate(
    val templatePath: TraceablePath,
    val moduleDirectory: Path,
) : PsiBuildProblem(Level.Error) {
    companion object {
        const val ID = "unresolved.template"
    }

    override val element: PsiElement
        get() = templatePath.extractPsiElement()

    override val buildProblemId: BuildProblemId = ID
    override val message: @Nls String
        get() {
            val relativePath = templatePath.value.relativeTo(moduleDirectory)
            return SchemaBundle.message("unresolved.template", relativePath)
        }
}