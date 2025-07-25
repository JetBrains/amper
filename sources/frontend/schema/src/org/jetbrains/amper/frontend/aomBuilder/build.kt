/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.aomBuilder

import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.amper.core.messages.ProblemReporter
import org.jetbrains.amper.core.system.DefaultSystemInfo
import org.jetbrains.amper.core.system.SystemInfo
import org.jetbrains.amper.frontend.AmperModule
import org.jetbrains.amper.frontend.BomDependency
import org.jetbrains.amper.frontend.MavenDependency
import org.jetbrains.amper.frontend.Model
import org.jetbrains.amper.frontend.Notation
import org.jetbrains.amper.frontend.VersionCatalog
import org.jetbrains.amper.frontend.api.Trace
import org.jetbrains.amper.frontend.api.TraceableString
import org.jetbrains.amper.frontend.api.trace
import org.jetbrains.amper.frontend.api.valueBase
import org.jetbrains.amper.frontend.catalogs.builtInCatalog
import org.jetbrains.amper.frontend.catalogs.plus
import org.jetbrains.amper.frontend.catalogs.substituteCatalogDependencies
import org.jetbrains.amper.frontend.contexts.MinimalModuleHolder
import org.jetbrains.amper.frontend.contexts.PathCtx
import org.jetbrains.amper.frontend.contexts.tryReadMinimalModule
import org.jetbrains.amper.frontend.diagnostics.AomModelDiagnosticFactories
import org.jetbrains.amper.frontend.diagnostics.AomSingleModuleDiagnosticFactories
import org.jetbrains.amper.frontend.diagnostics.MergedTreeDiagnostics
import org.jetbrains.amper.frontend.diagnostics.OwnedTreeDiagnostics
import org.jetbrains.amper.frontend.diagnostics.UnresolvedModuleDependency
import org.jetbrains.amper.frontend.messages.extractPsiElementOrNull
import org.jetbrains.amper.frontend.messages.originalFilePath
import org.jetbrains.amper.frontend.processing.addImplicitDependencies
import org.jetbrains.amper.frontend.processing.configureHotReloadDefaults
import org.jetbrains.amper.frontend.processing.configureLombokDefaults
import org.jetbrains.amper.frontend.processing.configureSpringBootDefaults
import org.jetbrains.amper.frontend.processing.substituteComposeOsSpecific
import org.jetbrains.amper.frontend.project.AmperProjectContext
import org.jetbrains.amper.frontend.schema.CatalogDependency
import org.jetbrains.amper.frontend.schema.Dependency
import org.jetbrains.amper.frontend.schema.ExternalMavenBomDependency
import org.jetbrains.amper.frontend.schema.ExternalMavenDependency
import org.jetbrains.amper.frontend.schema.InternalDependency
import org.jetbrains.amper.frontend.schema.Module
import org.jetbrains.amper.frontend.schema.ProductType
import org.jetbrains.amper.frontend.tree.MapLikeValue
import org.jetbrains.amper.frontend.tree.TreeRefiner
import org.jetbrains.amper.frontend.tree.appendDefaultValues
import org.jetbrains.amper.frontend.tree.reading.readTree
import org.jetbrains.amper.frontend.tree.resolveReferences
import org.jetbrains.amper.frontend.types.SchemaTypingContext
import org.jetbrains.amper.plugins.schema.model.PluginData
import java.nio.file.Path
import kotlin.io.path.absolute
import kotlin.io.path.name
import kotlin.io.path.relativeTo

/**
 * Parses the configuration files of this [AmperProjectContext] and builds the project model.
 *
 * All errors and warnings are reported to the given [problemReporter].
 *
 * If a fatal error occurs, the model cannot be built, and thus the method returns null. It's up to the consumer to
 * handle the fatal errors.
 */
context(problemReporter: ProblemReporter)
fun AmperProjectContext.readProjectModel(): Model? {
    val resultModules = doBuild(this@readProjectModel) ?: return null
    val model = DefaultModel(projectRootDir.toNioPath(), resultModules)
    AomModelDiagnosticFactories.forEach { it.analyze(model, problemReporter) }
    return model
}

/**
 * AOM build function, introduced for testing.
 */
context(problemReporter: ProblemReporter)
internal fun doBuild(
    projectContext: AmperProjectContext,
    systemInfo: SystemInfo = DefaultSystemInfo,
    pluginData: List<PluginData> = projectContext.loadPreparedPluginData(),
): List<AmperModule>? = with(
    BuildCtx(
        pathResolver = projectContext.frontendPathResolver,
        problemReporter = problemReporter,
        types = SchemaTypingContext(pluginData),
        systemInfo = systemInfo,
    )
) {
    // Parse all module files and perform preprocessing (templates, catalogs, etc.)
    val rawModules = projectContext.amperModuleFiles.mapNotNull {
        readModuleMergedTree(it, projectContext.projectVersionsCatalog)
    }

    // Fail fast if we have fatal errors.
    if (problemReporter.hasFatal) return null

    // Build [AmperModule]s.
    val modules = buildAmperModules(rawModules)

    // Do some alterations to the built modules.
    modules.forEach { it.module.addImplicitDependencies() }

    // Build custom tasks for relevant modules.
    projectContext.amperCustomTaskFiles.forEach { buildCustomTask(it, modules) }

    // Load plugins that exist in the project
    buildPlugins(pluginData, projectContext, modules)

    // Perform diagnostics.
    AomSingleModuleDiagnosticFactories.forEach { diagnostic ->
        modules.forEach { diagnostic.analyze(it.module, problemReporter) }
    }

    // Fail fast if we have fatal errors.
    if (problemReporter.hasFatal) return null

    return modules.map { it.module }
}

context(problemReporter: ProblemReporter)
internal fun BuildCtx.readModuleMergedTree(
    moduleFile: VirtualFile,
    projectVersionsCatalog: VersionCatalog?,
): ModuleBuildCtx? {
    val moduleCtx = PathCtx(moduleFile, moduleFile.asPsi().trace)

    // Read the initial module file.
    val minimalModule = tryReadMinimalModule(moduleFile) ?: return null

    // Read the whole module and used templates.
    // FIXME Read templates by raw access API and then just reuse single read tree both
    //       for module building and minimal module building.
    val ownedTrees = readWithTemplates(minimalModule, moduleFile, moduleCtx)

    // Perform diagnostics for owned trees.
    OwnedTreeDiagnostics.forEach { diagnostic ->
        ownedTrees.forEach { diagnostic.analyze(root = it, minimalModule.module, problemReporter) }
    }

    // Merge owned trees (see [TreeMerger]) and preprocess them.
    val preProcessedTree = treeMerger.mergeTrees(ownedTrees)
        .appendDefaultValues()
        .resolveReferences()

    // Choose catalogs.
    // TODO This should be done without refining somehow?
    val refiner = TreeRefiner(minimalModule.combinedInheritance)
    val commonTree = refiner.refineTree(preProcessedTree, setOf(moduleCtx))
    val commonModule = createSchemaNode<Module>(commonTree)
    val effectiveCatalog = projectVersionsCatalog + commonModule.settings.builtInCatalog()

    val processedTree = preProcessedTree
        .substituteCatalogDependencies(effectiveCatalog)
        .substituteComposeOsSpecific()
        .configureSpringBootDefaults(commonModule)
        .configureHotReloadDefaults(commonModule)
        .configureLombokDefaults(commonModule)

    // Perform diagnostics for the merged tree.
    MergedTreeDiagnostics(refiner).forEach { diagnostic ->
        diagnostic.analyze(processedTree, minimalModule.module, problemReporter)
    }

    return ModuleBuildCtx(
        moduleFile = moduleFile,
        mergedTree = processedTree,
        refiner = refiner,
        catalog = effectiveCatalog,
        moduleCtxModule = commonModule,
        commonTree = commonTree,
        buildCtx = this,
    )
}

internal fun BuildCtx.readWithTemplates(
    minimalModule: MinimalModuleHolder,
    mPath: VirtualFile,
    moduleCtx: PathCtx,
): List<MapLikeValue<*>> {
    val moduleTree = readTree(mPath, moduleAType, moduleCtx)
    return listOf(moduleTree) + minimalModule.appliedTemplates.mapNotNull {
        val templateVirtual = it.asVirtualOrNull() ?: return@mapNotNull null
        readTree(templateVirtual, templateAType, PathCtx(templateVirtual, templateVirtual.asPsi().trace))
    }
}

/**
 * Build and resolve internal module dependencies.
 */
context(_: ProblemReporter)
private fun BuildCtx.buildAmperModules(
    modules: List<ModuleBuildCtx>,
): List<ModuleBuildCtx> {
    val dir2module = modules.associate { it.moduleFile.parent.toNioPath() to it.module }
    val reportedUnresolvedModules = mutableSetOf<Trace>()

    modules.forEach { module ->
        // Do build seeds and propagate settings.
        val seeds = module.moduleCtxModule.buildFragmentSeeds()

        val moduleFragments = createFragments(seeds, module) {
            it.resolveInternalDependency(dir2module, reportedUnresolvedModules)
        }
        val (leaves, testLeaves) = moduleFragments.filterIsInstance<DefaultLeafFragment>().partition { !it.isTest }

        module.module.apply {
            fragments = moduleFragments
            artifacts = createArtifacts(false, module.module.type, leaves) +
                    createArtifacts(true, module.module.type, testLeaves)
        }
    }

    return modules
}

private fun createArtifacts(
    isTest: Boolean,
    productType: ProductType,
    fragments: List<DefaultLeafFragment>,
): List<DefaultArtifact> = when (productType) {
    ProductType.LIB -> listOf(DefaultArtifact(if (!isTest) "lib" else "testLib", fragments, isTest))
    else -> fragments.map { DefaultArtifact(it.name, listOf(it), isTest) }
}

/**
 * Resolve internal modules against known ones by path.
 */
context(_: ProblemReporter)
private fun Dependency.resolveInternalDependency(
    moduleDir2module: Map<Path, AmperModule>,
    reportedUnresolvedModules: MutableSet<Trace>,
): Notation = when (this) {
    is ExternalMavenDependency -> MavenDependency(
        coordinates = TraceableString(coordinates, this::coordinates.valueBase!!.trace),
        trace = trace,
        compile = scope.compile,
        runtime = scope.runtime,
        exported = exported,
    )
    is InternalDependency -> resolveModuleDependency(trace, moduleDir2module, reportedUnresolvedModules)
    is ExternalMavenBomDependency -> BomDependency(
        coordinates = TraceableString(coordinates, trace = ::coordinates.valueBase?.trace),
        trace = trace,
    )
    is CatalogDependency -> error("Catalog dependency must be processed earlier!")
    else -> error("Unknown dependency type: ${this::class}")
}

context(problemReporter: ProblemReporter)
private fun InternalDependency.resolveModuleDependency(
    trace: Trace?,
    moduleDir2module: Map<Path, AmperModule>,
    reportedUnresolvedModules: MutableSet<Trace>,
): DefaultLocalModuleDependency {
    val module = moduleDir2module[path]
    if (module == null) {
        val trace = this.trace
        val originalDirectory = trace?.extractPsiElementOrNull()?.originalFilePath?.parent?.absolute()
        // Do not report the same error twice from different fragments.
        if (trace != null && originalDirectory != null && reportedUnresolvedModules.add(trace)) {
            val possibleCorrectPath = moduleDir2module.keys
                .find { it.name == path.name }
                ?.relativeTo(originalDirectory)

            problemReporter.reportMessage(
                UnresolvedModuleDependency(this, originalDirectory, possibleCorrectPath)
            )
        }
    }

    return DefaultLocalModuleDependency(
        module = module ?: NotResolvedModule(userReadableName = path.name, invalidPath = path),
        path = path,
        trace = trace,
        compile = scope.compile,
        runtime = scope.runtime,
        exported = exported,
    )
}
