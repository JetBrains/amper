/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.aomBuilder

import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.amper.core.UsedInIdePlugin
import org.jetbrains.amper.frontend.AmperModule
import org.jetbrains.amper.frontend.BomDependency
import org.jetbrains.amper.frontend.DefaultScopedNotation
import org.jetbrains.amper.frontend.MavenCoordinates
import org.jetbrains.amper.frontend.MavenDependency
import org.jetbrains.amper.frontend.Model
import org.jetbrains.amper.frontend.Notation
import org.jetbrains.amper.frontend.VersionCatalog
import org.jetbrains.amper.frontend.aomBuilder.plugins.buildPlugins
import org.jetbrains.amper.frontend.api.DefaultTrace
import org.jetbrains.amper.frontend.api.Trace
import org.jetbrains.amper.frontend.api.TraceableString
import org.jetbrains.amper.frontend.api.asTrace
import org.jetbrains.amper.frontend.api.schemaDelegate
import org.jetbrains.amper.frontend.catalogs.builtInCatalog
import org.jetbrains.amper.frontend.catalogs.substituteCatalogDependencies
import org.jetbrains.amper.frontend.contexts.MinimalModuleHolder
import org.jetbrains.amper.frontend.contexts.PathCtx
import org.jetbrains.amper.frontend.contexts.tryReadMinimalModule
import org.jetbrains.amper.frontend.diagnostics.AomModelDiagnosticFactories
import org.jetbrains.amper.frontend.diagnostics.AomSingleModuleDiagnosticFactories
import org.jetbrains.amper.frontend.diagnostics.treeDiagnostics
import org.jetbrains.amper.frontend.diagnostics.UnresolvedModuleDependency
import org.jetbrains.amper.frontend.messages.extractPsiElementOrNull
import org.jetbrains.amper.frontend.messages.originalFilePath
import org.jetbrains.amper.frontend.plus
import org.jetbrains.amper.frontend.processing.addImplicitDependencies
import org.jetbrains.amper.frontend.processing.configureLombokDefaults
import org.jetbrains.amper.frontend.processing.configurePluginDefaults
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
import org.jetbrains.amper.frontend.schema.Settings
import org.jetbrains.amper.frontend.tree.MappingNode
import org.jetbrains.amper.frontend.tree.RefinedMappingNode
import org.jetbrains.amper.frontend.tree.TreeRefiner
import org.jetbrains.amper.frontend.tree.get
import org.jetbrains.amper.frontend.tree.mergeTrees
import org.jetbrains.amper.frontend.tree.reading.readTree
import org.jetbrains.amper.frontend.tree.resolveReferences
import org.jetbrains.amper.frontend.types.SchemaTypingContext
import org.jetbrains.amper.frontend.types.maven.MavenPluginXml
import org.jetbrains.amper.plugins.schema.model.PluginData
import org.jetbrains.amper.problems.reporting.ProblemReporter
import org.jetbrains.amper.stdlib.caching
import org.jetbrains.amper.system.info.SystemInfo
import java.nio.file.Path
import kotlin.io.path.absolute
import kotlin.io.path.name
import kotlin.io.path.relativeTo
import kotlin.reflect.KProperty0

/**
 * Parses the configuration files of this [AmperProjectContext] and builds the project model.
 *
 * All errors and warnings are reported to the given [problemReporter].
 *
 * The returned model is built on a best-effort basis. The contracts for all returned data is only respected if no
 * errors are reported.
 *
 * @param pluginData plugin data that should be used for reading the project model. The default is pre-built plugin
 *  data but the client is free to provide their own. E.g., IDE can build the freshest plugin data directly from the
 *  Kotlin sources in-memory and provide it to the project model reader.
 */
@UsedInIdePlugin
context(problemReporter: ProblemReporter)
fun AmperProjectContext.readProjectModel(
    pluginData: List<PluginData>,
    mavenPluginXmls: List<MavenPluginXml>,
): Model = doReadProjectModel(pluginData, mavenPluginXmls)

/**
 * Testable version of [readProjectModel].
 */
context(problemReporter: ProblemReporter)
internal fun AmperProjectContext.doReadProjectModel(
    pluginData: List<PluginData>,
    mavenPluginXmls: List<MavenPluginXml>,
    systemInfo: SystemInfo = SystemInfo.CurrentHost,
): Model = with(
    BuildCtx(
        pathResolver = frontendPathResolver,
        problemReporter = problemReporter,
        types = SchemaTypingContext(pluginData, mavenPluginXmls),
        systemInfo = systemInfo,
    )
) {
    // Parse all module files and perform preprocessing (templates, catalogs, etc.)
    val rawModulesByFile = caching { templateCache ->
        amperModuleFiles.associateWith {
            readModuleMergedTree(it, projectVersionsCatalog, templateCache)
        }
    }

    val unreadableModuleFiles = rawModulesByFile.filterValues { it == null }.keys
    val rawModules = rawModulesByFile.values.filterNotNull()

    // Build [AmperModule]s.
    val modules = buildAmperModules(rawModules)

    // Do some alterations to the built modules.
    modules.forEach { it.module.addImplicitDependencies() }

    // Load plugins that exist in the project
    buildPlugins(pluginData, projectContext = this@doReadProjectModel, modules)
    
    // Add read maven plugin xmls.
    modules.forEach { it.module.amperMavenPluginsDescriptions = mavenPluginXmls }

    // Perform diagnostics.
    AomSingleModuleDiagnosticFactories.forEach { diagnostic ->
        modules.forEach { diagnostic.analyze(it.module, problemReporter) }
    }
    val model = DefaultModel(
        projectRoot = projectRootDir.toNioPath(),
        modules = modules.map { it.module },
        unreadableModuleFiles = unreadableModuleFiles,
    )
    AomModelDiagnosticFactories.forEach { it.analyze(model, problemReporter) }
    return model
}

context(problemReporter: ProblemReporter)
internal fun BuildCtx.readModuleMergedTree(
    moduleFile: VirtualFile,
    projectVersionsCatalog: VersionCatalog?,
    templatesCache: MutableMap<Path, MappingNode> = hashMapOf(),
): ModuleBuildCtx? {
    val moduleCtx = PathCtx(moduleFile, moduleFile.asPsi().asTrace())

    // Read the initial module file.
    val minimalModule = tryReadMinimalModule(moduleFile) ?: return null

    // Read the whole module and used templates.
    // FIXME Read templates by raw access API and then just reuse single read tree both
    //       for module building and minimal module building.
    val ownedTrees = readWithTemplates(minimalModule, moduleFile, moduleCtx, templatesCache)

    val modulePsiDir = pathResolver.toPsiDirectory(moduleFile.parent) ?: error("A module file necessarily has a parent")

    val preProcessedTree = mergeTrees(ownedTrees)
        .configurePluginDefaults(moduleDir = modulePsiDir, product = minimalModule.module.product)

    // TODO This should be done without refining somehow?
    val refiner = TreeRefiner(minimalModule.combinedInheritance)

    // Create a common "preview" of module settings to finish dependent/reactive configuration.
    val preProcessedCommonSettings: Settings = run {
        val preProcessedCommonTree = refiner.refineTree(preProcessedTree, setOf(moduleCtx))
            .resolveReferences()
        createSchemaNode(preProcessedCommonTree[Module::settings]!!) ?: return null
    }
    val effectiveCatalog = preProcessedCommonSettings.builtInCatalog() + projectVersionsCatalog

    // Finish preparing the tree with the dependent/reactive configuration.
    val processedTree = preProcessedTree
        .substituteCatalogDependencies(effectiveCatalog)
        .substituteComposeOsSpecific()  // must be after catalog substitution
        .configureSpringBootDefaults(preProcessedCommonSettings.springBoot)
        .configureLombokDefaults(preProcessedCommonSettings.lombok)

    // Create a common `Module` schema node needed later for `AmperModule` configuration.
    val processedCommonTree = refiner.refineTree(processedTree, setOf(moduleCtx))
        .resolveReferences()
    val commonModule = createSchemaNode<Module>(processedCommonTree)
        ?: return null

    // safe: `plugins` has a default
    val pluginsTree = processedCommonTree[Module::plugins] as RefinedMappingNode

    treeDiagnostics(refiner).forEach { diagnostic ->
        diagnostic.analyze(processedTree, minimalModule.module, problemReporter)
    }

    return ModuleBuildCtx(
        moduleFile = moduleFile,
        mergedTree = processedTree,
        refiner = refiner,
        catalog = effectiveCatalog,
        moduleCtxModule = commonModule,
        pluginsTree = pluginsTree,
        buildCtx = this,
    )
}

internal fun BuildCtx.readWithTemplates(
    minimalModule: MinimalModuleHolder,
    mPath: VirtualFile,
    moduleCtx: PathCtx,
    templatesCache: MutableMap<Path, MappingNode> = hashMapOf(),
): List<MappingNode> {
    val moduleTree = readTree(mPath, moduleAType, moduleCtx)
    return listOf(moduleTree) + minimalModule.appliedTemplates.mapNotNull {
        templatesCache.getOrPut(it) {
            val templateVirtual = it.asVirtualOrNull() ?: return@mapNotNull null
            val psiFile = pathResolver.toPsiFile(templateVirtual) ?: return@mapNotNull null
            readTree(templateVirtual, templateAType, PathCtx(templateVirtual, psiFile.asTrace()))
        }
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
            pluginSettings = module.moduleCtxModule.plugins
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
): Notation? = when (this) {
    is ExternalMavenDependency -> MavenDependency(
        coordinates = ::coordinates.traceableString().mavenCoordinates(),
        trace = trace,
        compile = scope.compile,
        runtime = scope.runtime,
        exported = exported,
    )
    is InternalDependency -> resolveModuleDependency(moduleDir2module, reportedUnresolvedModules)
    is ExternalMavenBomDependency -> BomDependency(
        coordinates = ::coordinates.traceableString().mavenCoordinates(),
        trace = trace,
    )
    is CatalogDependency -> error("Catalog dependency must be processed earlier!")
    else -> error("Unknown dependency type: ${this::class}")
}

fun KProperty0<String>.traceableString(): TraceableString {
    return TraceableString(get(), schemaDelegate.trace)
}

fun String.mavenCoordinates(): MavenCoordinates = TraceableString(this, DefaultTrace).mavenCoordinates()

/**
* Full maven format contains 2-4 parts delimited with ":" with optional packaginType delimited by "@" at the end.
* groupId:artifactId[:version][:classifier][@packagingType]
*/
fun TraceableString.mavenCoordinates(): MavenCoordinates {
    val parts = value.trim().split(":").toMutableList()
    check(parts.size in 2..4) {
        "Not reached: coordinates should have between 2 and 4 parts, but got ${parts.size}: $this. " +
                "Ensure that the coordinates were properly validated in the parser."
    }
    val packagingType = parts[parts.size - 1].substringAfterLast("@", "").ifBlank { null }

    // Drop packagingType from the last part
    parts[parts.size - 1] = parts[parts.size - 1].substringBeforeLast("@")

    return MavenCoordinates(
        groupId = parts[0],
        artifactId = parts[1],
        version = if (parts.size > 2) parts[2] else null,
        classifier = if (parts.size > 3) parts[3] else null,
        packagingType = packagingType,
        trace = this.trace,
    )
}

context(problemReporter: ProblemReporter)
private fun InternalDependency.resolveModuleDependency(
    moduleDir2module: Map<Path, AmperModule>,
    reportedUnresolvedModules: MutableSet<Trace>,
): DefaultScopedNotation? {
    val module = moduleDir2module[path]
    if (module == null) {
        val originalDirectory = trace.extractPsiElementOrNull()?.originalFilePath?.parent?.absolute()
        // Do not report the same error twice from different fragments.
        if (originalDirectory != null && reportedUnresolvedModules.add(trace)) {
            val possibleCorrectPath = moduleDir2module.keys
                .find { it.name == path.name }
                ?.relativeTo(originalDirectory)

            problemReporter.reportMessage(
                UnresolvedModuleDependency(this, originalDirectory, possibleCorrectPath)
            )
        }
        return null
    }

    return DefaultLocalModuleDependency(
        module = module,
        path = path,
        trace = trace,
        compile = scope.compile,
        runtime = scope.runtime,
        exported = exported,
    )
}
