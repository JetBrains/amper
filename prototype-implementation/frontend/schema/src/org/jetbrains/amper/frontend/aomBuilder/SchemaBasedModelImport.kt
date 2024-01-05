/*
 * Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.aomBuilder

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import org.jetbrains.amper.core.Result
import org.jetbrains.amper.core.amperFailure
import org.jetbrains.amper.core.asAmperSuccess
import org.jetbrains.amper.core.messages.ProblemReporterContext
import org.jetbrains.amper.frontend.FrontendPathResolver
import org.jetbrains.amper.frontend.Model
import org.jetbrains.amper.frontend.ModelInit
import java.nio.file.Path

class SchemaBasedModelImport : ModelInit {
    override val name = "schema-based"

    context(ProblemReporterContext)
    override fun getModel(root: Path): Result<Model> {
        val fioCtx = DefaultFioContext(root)
        val pathResolver = FrontendPathResolver()
        val resultModules = doBuild(pathResolver, fioCtx,)
            ?: return amperFailure()
        // Propagate parts from fragment to fragment.
        return DefaultModel(resultModules + fioCtx.gradleModules.values).resolved.asAmperSuccess()
    }

    context(ProblemReporterContext)
    override fun getModel(root: PsiFile, project: Project): Result<Model> {
        val fioCtx = DefaultFioContext(root.virtualFile.toNioPath())
        val pathResolver = FrontendPathResolver(project = project)
        val resultModules = doBuild(pathResolver, fioCtx,)
            ?: return amperFailure()
        // Propagate parts from fragment to fragment.
        return DefaultModel(resultModules + fioCtx.gradleModules.values).resolved.asAmperSuccess()
    }
}