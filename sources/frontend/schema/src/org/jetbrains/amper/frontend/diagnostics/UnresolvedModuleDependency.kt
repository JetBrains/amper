/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.diagnostics

import com.intellij.psi.PsiElement
import org.jetbrains.amper.core.messages.BuildProblemId
import org.jetbrains.amper.core.messages.Level
import org.jetbrains.amper.frontend.SchemaBundle
import org.jetbrains.amper.frontend.messages.PsiBuildProblem
import org.jetbrains.amper.frontend.messages.extractPsiElement
import org.jetbrains.amper.frontend.schema.InternalDependency
import org.jetbrains.annotations.Nls
import java.nio.file.Path
import kotlin.io.path.pathString
import kotlin.io.path.relativeTo

class UnresolvedModuleDependency(
    val dependency: InternalDependency,
    val moduleDirectory: Path,
    val possibleCorrectPath: Path?,
) : PsiBuildProblem(Level.Error) {
    companion object {
        const val ID = "unresolved.module"
    }

    override val element: PsiElement
        get() = dependency::path.extractPsiElement()

    override val buildProblemId: BuildProblemId = ID

    override val message: @Nls String
        get() {
            val relativePath = dependency.path.relativeTo(moduleDirectory)

            return if (possibleCorrectPath == null) {
                SchemaBundle.message(
                    messageKey = "unresolved.module",
                    relativePath.formatModulePath()
                )
            } else {
                SchemaBundle.message(
                    messageKey = "unresolved.module.with.hint",
                    relativePath.formatModulePath(),
                    possibleCorrectPath.formatModulePath()
                )
            }
        }

    private fun Path.formatModulePath(): String {
        val pathString = pathString
        // If a relative path starts from the current folder, it should be prepended with ./ to distinguish it from Maven dependency
        val relativeToCurrent = if (pathString.startsWith(".")) pathString else "./$pathString"
        // Module paths are always using / as delimiter
        return relativeToCurrent.replace('\\', '/')
    }
}
