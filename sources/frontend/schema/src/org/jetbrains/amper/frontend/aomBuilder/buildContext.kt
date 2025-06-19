/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.aomBuilder

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import org.jetbrains.amper.core.messages.ProblemReporterContext
import org.jetbrains.amper.core.system.DefaultSystemInfo
import org.jetbrains.amper.core.system.SystemInfo
import org.jetbrains.amper.frontend.AmperModuleFileSource
import org.jetbrains.amper.frontend.FrontendPathResolver
import org.jetbrains.amper.frontend.VersionCatalog
import org.jetbrains.amper.frontend.catalogs.VersionsCatalogProvider
import org.jetbrains.amper.frontend.meta.ATypesDiscoverer
import org.jetbrains.amper.frontend.schema.Module
import org.jetbrains.amper.frontend.schema.Project
import org.jetbrains.amper.frontend.schema.Template
import org.jetbrains.amper.frontend.tree.Merged
import org.jetbrains.amper.frontend.tree.TreeMerger
import org.jetbrains.amper.frontend.tree.TreeRefiner
import org.jetbrains.amper.frontend.tree.TreeValue
import org.jetbrains.amper.frontend.types.AmperTypes
import java.nio.file.Path


internal fun ProblemReporterContext.BuildCtx(
    catalogProvider: VersionsCatalogProvider,
    treeMerger: TreeMerger = TreeMerger(),
    types: AmperTypes = ATypesDiscoverer,
    systemInfo: SystemInfo = DefaultSystemInfo,
) = BuildCtx(
    pathResolver = catalogProvider.frontendPathResolver,
    problemReporterCtx = this,
    treeMerger = treeMerger,
    types = types,
    catalogFinder = catalogProvider,
    systemInfo = systemInfo,
)

internal data class BuildCtx(
    val pathResolver: FrontendPathResolver,
    val problemReporterCtx: ProblemReporterContext,
    val treeMerger: TreeMerger = TreeMerger(),
    val types: AmperTypes = ATypesDiscoverer,
    val catalogFinder: VersionsCatalogProvider? = null,
    val systemInfo: SystemInfo = DefaultSystemInfo,
) : ProblemReporterContext by problemReporterCtx {

    val moduleAType = types<Module>()
    val templateAType = types<Template>()
    val projectAType = types<Project>()

    // TODO Properly handle null cases of `loadVirtualFile`.
    fun VirtualFile.asPsi(): PsiFile = pathResolver.toPsiFile(this) ?: error("No $this file")
    fun Path.asPsi(): PsiFile = pathResolver.toPsiFile(pathResolver.loadVirtualFile(this@asPsi)) ?: error("No $this file")
    fun Path.asVirtualOrNull() = pathResolver.loadVirtualFileOrNull(this)
}

internal data class ModuleBuildCtx(
    val moduleFile: VirtualFile,
    val mergedTree: TreeValue<Merged>,
    val refiner: TreeRefiner,
    val catalog: VersionCatalog,
    val buildCtx: BuildCtx,

    /**
     * Module which has settings that do not contain any platform, test, etc. contexts.
     */
    val moduleCtxModule: Module,
) {
    val module by lazy {
        with(buildCtx) {
            DefaultModule(
                userReadableName = moduleFile.parent.name,
                type = moduleCtxModule.product.type,
                source = AmperModuleFileSource(moduleFile.toNioPath()),
                usedCatalog = catalog,
                usedTemplates = moduleCtxModule.apply?.map { buildCtx.pathResolver.loadVirtualFile(it.value) }.orEmpty(),
                parts = moduleCtxModule.convertModuleParts(),
            )
        }
    }
}

internal val ModuleBuildCtx.moduleDirPath get() = moduleFile.parent.toNioPath()