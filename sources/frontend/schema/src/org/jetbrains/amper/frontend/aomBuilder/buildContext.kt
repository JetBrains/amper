/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.aomBuilder

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import org.jetbrains.amper.core.messages.ProblemReporter
import org.jetbrains.amper.core.system.DefaultSystemInfo
import org.jetbrains.amper.core.system.SystemInfo
import org.jetbrains.amper.frontend.AmperModuleFileSource
import org.jetbrains.amper.frontend.FrontendPathResolver
import org.jetbrains.amper.frontend.VersionCatalog
import org.jetbrains.amper.frontend.api.TraceablePath
import org.jetbrains.amper.frontend.asBuildProblemSource
import org.jetbrains.amper.frontend.reportBundleError
import org.jetbrains.amper.frontend.schema.Module
import org.jetbrains.amper.frontend.schema.Project
import org.jetbrains.amper.frontend.schema.Template
import org.jetbrains.amper.frontend.tree.Merged
import org.jetbrains.amper.frontend.tree.Refined
import org.jetbrains.amper.frontend.tree.TreeMerger
import org.jetbrains.amper.frontend.tree.TreeRefiner
import org.jetbrains.amper.frontend.tree.TreeValue
import org.jetbrains.amper.frontend.types.SchemaTypingContext
import org.jetbrains.amper.frontend.types.getDeclaration
import java.nio.file.Path

internal data class BuildCtx(
    val pathResolver: FrontendPathResolver,
    val problemReporter: ProblemReporter,
    val treeMerger: TreeMerger = TreeMerger(),
    val types: SchemaTypingContext = SchemaTypingContext(emptyList()),
    val systemInfo: SystemInfo = DefaultSystemInfo,
) {
    val moduleAType = types.getDeclaration<Module>()
    val templateAType = types.getDeclaration<Template>()
    val projectAType = types.getDeclaration<Project>()

    // TODO Properly handle null cases of `loadVirtualFile`.
    fun VirtualFile.asPsi(): PsiFile = pathResolver.toPsiFile(this) ?: error("No $this file")
    fun Path.asVirtualOrNull() = pathResolver.loadVirtualFileOrNull(this)
}

internal data class ModuleBuildCtx(
    val moduleFile: VirtualFile,
    val mergedTree: Merged,
    val refiner: TreeRefiner,
    val catalog: VersionCatalog,
    val buildCtx: BuildCtx,
    val commonTree: TreeValue<Refined>,

    /**
     * Module which has settings that do not contain any platform, test, etc. contexts.
     */
    val moduleCtxModule: Module,
) {
    val module by lazy {
        with(buildCtx.problemReporter) {
            DefaultModule(
                userReadableName = moduleFile.parent.name,
                type = moduleCtxModule.product.type,
                source = AmperModuleFileSource(moduleFile.toNioPath()),
                usedCatalog = catalog,
                usedTemplates = moduleCtxModule.apply?.mapNotNull(::readTemplateFromPath).orEmpty(),
                parts = moduleCtxModule.convertModuleParts(),
            )
        }
    }

    /**
     * TODO: A better solution would be to allow marking Paths in the tree as "should exist" and provide a general
     *   diagnostic allowing to check that during the tree analysis. This diagnostic, however, should have its message
     *   customizable.
     */
    private fun readTemplateFromPath(templatePath: TraceablePath): VirtualFile? {
        val path = buildCtx.pathResolver.loadVirtualFileOrNull(templatePath.value)
        if (path == null) {
            val relativePath = moduleFile.parent.toNioPath().relativize(templatePath.value)
            buildCtx.problemReporter.reportBundleError(
                source = templatePath.asBuildProblemSource(),
                messageKey = "unresolved.template",
                relativePath,
            )
        }
        return path
    }
}

internal val ModuleBuildCtx.moduleDirPath get() = moduleFile.parent.toNioPath()