/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.aomBuilder

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import org.jetbrains.amper.frontend.AmperModuleFileSource
import org.jetbrains.amper.frontend.FrontendPathResolver
import org.jetbrains.amper.frontend.Layout
import org.jetbrains.amper.frontend.VersionCatalog
import org.jetbrains.amper.frontend.api.TraceablePath
import org.jetbrains.amper.frontend.diagnostics.UnresolvedTemplate
import org.jetbrains.amper.frontend.schema.Module
import org.jetbrains.amper.frontend.tree.MappingNode
import org.jetbrains.amper.frontend.tree.RefinedMappingNode
import org.jetbrains.amper.frontend.tree.TreeRefiner
import org.jetbrains.amper.problems.reporting.ProblemReporter
import java.nio.file.Path

context(pathResolver: FrontendPathResolver)
internal fun VirtualFile.asPsi(): PsiFile = pathResolver.toPsiFile(this) ?: error("No $this file")

context(pathResolver: FrontendPathResolver)
internal fun Path.asVirtualOrNull() = pathResolver.loadVirtualFileOrNull(this)

internal class ModuleBuildCtx(
    val moduleFile: VirtualFile,
    val mergedTree: MappingNode,
    val refiner: TreeRefiner,
    val catalog: VersionCatalog,
    val pluginsTree: RefinedMappingNode,

    /**
     * Module which has settings that do not contain any platform, test, etc. contexts.
     */
    val moduleCtxModule: Module,

    pathResolver: FrontendPathResolver,
    problemReporter: ProblemReporter,
) {
    val module by lazy {
        context(problemReporter, pathResolver) {
            DefaultModule(
                userReadableName = moduleFile.parent.name,
                type = moduleCtxModule.product.type,
                aliases = moduleCtxModule.aliases.orEmpty().entries.associate { alias ->
                    alias.key.value to alias.value.map { platform -> platform.value }.toSet()
                },
                source = AmperModuleFileSource(moduleFile.toNioPath()),
                usedCatalog = catalog,
                usedTemplates = moduleCtxModule.apply?.mapNotNull { readTemplateFromPath(it) }.orEmpty(),
                parts = moduleCtxModule.convertModuleParts(),
                layout = Layout.valueOf(moduleCtxModule.layout.name),
            )
        }
    }

    /**
     * TODO: A better solution would be to allow marking Paths in the tree as "should exist" and provide a general
     *   diagnostic allowing to check that during the tree analysis. This diagnostic, however, should have its message
     *   customizable.
     */
    context(problemReporter: ProblemReporter, pathResolver: FrontendPathResolver)
    private fun readTemplateFromPath(templatePath: TraceablePath): VirtualFile? {
        val path = pathResolver.loadVirtualFileOrNull(templatePath.value)
        if (path == null) {
            problemReporter.reportMessage(
                UnresolvedTemplate(
                    templatePath = templatePath,
                    moduleDirectory = moduleFile.parent.toNioPath(),
                )
            )
        }
        return path
    }
}
