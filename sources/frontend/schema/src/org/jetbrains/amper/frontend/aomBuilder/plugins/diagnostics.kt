/*
 * Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.aomBuilder.plugins

import com.intellij.psi.PsiElement
import org.jetbrains.amper.frontend.SchemaBundle
import org.jetbrains.amper.frontend.messages.PsiBuildProblem
import org.jetbrains.amper.problems.reporting.BuildProblemType
import org.jetbrains.amper.problems.reporting.DiagnosticId
import org.jetbrains.amper.problems.reporting.Level
import org.jetbrains.annotations.Nls
import java.nio.file.Path

class PluginYamlMissing(
    override val element: PsiElement,
    val expectedPluginYamlPath: Path,
) : PsiBuildProblem(
    level = Level.Warning,
    type = BuildProblemType.Generic,
) {
    @Deprecated("Should be replaced with `diagnosticId` property", replaceWith = ReplaceWith("diagnosticId"))
    override val buildProblemId get() = ID
    override val diagnosticId: DiagnosticId = PluginDiagnosticId.PluginYamlMissing
    override val message: @Nls String = SchemaBundle.message("plugin.missing.plugin.yaml")

    companion object {
        const val ID = "plugin.missing.plugin.yaml"
    }
}
