/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.aomBuilder

import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.amper.core.messages.ProblemReporterContext
import org.jetbrains.amper.core.system.DefaultSystemInfo
import org.jetbrains.amper.core.system.SystemInfo
import org.jetbrains.amper.frontend.DefaultScopedNotation
import org.jetbrains.amper.frontend.FrontendPathResolver
import org.jetbrains.amper.frontend.MavenDependency
import org.jetbrains.amper.frontend.PotatoModule
import org.jetbrains.amper.frontend.PotatoModuleDependency
import org.jetbrains.amper.frontend.PotatoModuleFileSource
import org.jetbrains.amper.frontend.VersionCatalog
import org.jetbrains.amper.frontend.api.Trace
import org.jetbrains.amper.frontend.api.withTraceFrom
import org.jetbrains.amper.frontend.diagnostics.AomSingleModuleDiagnosticFactories
import org.jetbrains.amper.frontend.diagnostics.IsmDiagnosticFactories
import org.jetbrains.amper.frontend.processing.BuiltInCatalog
import org.jetbrains.amper.frontend.processing.CompositeVersionCatalog
import org.jetbrains.amper.frontend.processing.parseGradleVersionCatalog
import org.jetbrains.amper.frontend.processing.readTemplatesAndMerge
import org.jetbrains.amper.frontend.processing.replaceCatalogDependencies
import org.jetbrains.amper.frontend.processing.replaceComposeOsSpecific
import org.jetbrains.amper.frontend.processing.withImplicitDependencies
import org.jetbrains.amper.frontend.schema.Base
import org.jetbrains.amper.frontend.schema.CatalogDependency
import org.jetbrains.amper.frontend.schema.Dependency
import org.jetbrains.amper.frontend.schema.ExternalMavenDependency
import org.jetbrains.amper.frontend.schema.InternalDependency
import org.jetbrains.amper.frontend.schema.Module
import org.jetbrains.amper.frontend.schema.ProductType
import org.jetbrains.amper.frontend.schema.noModifiers
import org.jetbrains.amper.frontend.schemaConverter.psi.ConvertCtx
import org.jetbrains.amper.frontend.schemaConverter.psi.convertModule
import java.nio.file.Path
import kotlin.io.path.name
import kotlin.io.path.pathString

/**
 * Module wrapper to hold also chosen catalog.
 */
data class ModuleHolder(
    val module: Module,
    val chosenCatalog: VersionCatalog?,
)

/**
 * AOM build function, introduced for testing.
 */
context(ProblemReporterContext)
internal fun doBuild(
    pathResolver: FrontendPathResolver,
    fioCtx: FioContext,
    systemInfo: SystemInfo = DefaultSystemInfo,
): List<PotatoModule>? {
    // Parse all module files and perform preprocessing (templates, catalogs, etc.)
    val path2SchemaModule = fioCtx.amperModuleFiles
        .mapNotNull { moduleFile ->
            // Read initial module file.
            val nonProcessed = with(pathResolver) {
                with(ConvertCtx(moduleFile.parent, pathResolver)) {
                    // TODO Report when file is not found.
                    convertModule(moduleFile)?.readTemplatesAndMerge(fioCtx)
                }
            } ?: return@mapNotNull null

            // Choose catalogs.
            val chosenCatalog = with(pathResolver) { tryGetCatalogFor(fioCtx, moduleFile, nonProcessed) }

            // Process module file.
            val processedModule = with(systemInfo) {
                nonProcessed
                    .replaceCatalogDependencies(chosenCatalog)
                    .replaceComposeOsSpecific()
            }

            IsmDiagnosticFactories.forEach {
                with(it) { processedModule.analyze() }
            }

            // Return result module.
            moduleFile to ModuleHolder(processedModule, chosenCatalog)
        }
        .toMap()

    // Fail fast if we have fatal errors.
    if (problemReporter.hasFatal) return null

    // Build AOM from ISM.
    return path2SchemaModule
        .buildAom(fioCtx.gradleModules)
        .map { it.withImplicitDependencies() }
        .onEach { module -> AomSingleModuleDiagnosticFactories.forEach { with(it) { module.analyze() } } }
}

/**
 * Try to find gradle catalog and compose it with built-in catalog.
 */
context(ProblemReporterContext, FrontendPathResolver)
fun tryGetCatalogFor(fioCtx: FioContext, file: VirtualFile, nonProcessed: Base): VersionCatalog {
    val gradleCatalog = fioCtx.getCatalogPathFor(file)
        ?.let { parseGradleVersionCatalog(it) }
    val compositeCatalog = addBuiltInCatalog(nonProcessed, gradleCatalog)
    return compositeCatalog
}

/**
 * Try to get used version catalog.
 */
context(ProblemReporterContext)
fun addBuiltInCatalog(
    nonProcessed: Base,
    otherCatalog: VersionCatalog? = null,
): VersionCatalog {
    val compose = nonProcessed.settings[noModifiers]?.compose
    // resolve compose version only if compose is enabled
    val chosenComposeVersion = compose?.version?.takeIf { compose.enabled }
    val builtInCatalog = BuiltInCatalog(composeVersion = chosenComposeVersion)
    val catalogs = otherCatalog?.let { listOf(it) }.orEmpty() + builtInCatalog
    val compositeCatalog = CompositeVersionCatalog(catalogs)
    return compositeCatalog
}

private data class ModuleTriple(
    val buildFile: VirtualFile,
    val schemaModule: Module,
    val module: DefaultModule,
)

/**
 * Build and resolve internal module dependencies.
 */
context(ProblemReporterContext)
internal fun Map<VirtualFile, ModuleHolder>.buildAom(
    gradleModules: Map<VirtualFile, PotatoModule>,
): List<PotatoModule> {
    val modules = map { (mPath, holder) ->
        // TODO Remove duplicating enums.
        ModuleTriple(
            buildFile = mPath,
            schemaModule = holder.module,
            module = DefaultModule(
                userReadableName = mPath.parent.name,
                type = holder.module.product.type,
                source = PotatoModuleFileSource(mPath.toNioPath()),
                origin = holder.module,
                usedCatalog = holder.chosenCatalog,
            )
        )
    }

    val moduleDir2module = (modules
        .associate { (path, _, module) -> path.parent to module } + gradleModules)
        .mapKeys { (k, _) -> k.toNioPath() }

    modules.forEach { (modulePath, schemaModule, module) ->
        val seeds = schemaModule.buildFragmentSeeds()
        val moduleFragments = createFragments(seeds, modulePath, module) { it.resolveInternalDependency(moduleDir2module) }
        val propagatedFragments = moduleFragments.withPropagatedSettings()
        val (leaves, testLeaves) = moduleFragments.filterIsInstance<DefaultLeafFragment>().partition { !it.isTest }

        module.apply {
            fragments = propagatedFragments
            artifacts = createArtifacts(false, module.type, leaves) +
                    createArtifacts(true, module.type, testLeaves)
        }
    }

    return modules.map { it.module }
}

private fun createArtifacts(
    isTest: Boolean,
    productType: ProductType,
    fragments: List<DefaultLeafFragment>
): List<DefaultArtifact> = when (productType) {
    ProductType.LIB -> listOf(DefaultArtifact(if (!isTest) "lib" else "testLib", fragments, isTest))
    else -> fragments.map { DefaultArtifact(it.name, listOf(it), isTest) }
}

class DefaultPotatoModuleDependency(
    override val module: PotatoModule,
    val path: Path,
    override val compile: Boolean = true,
    override val runtime: Boolean = true,
    override val exported: Boolean = false,
) : PotatoModuleDependency, DefaultScopedNotation {
    override var trace: Trace? = null

    override fun toString(): String {
        return "InternalDependency(module=${path.pathString})"
    }
}

/**
 * Resolve internal modules against known ones by path.
 */
context(ProblemReporterContext)
private fun Dependency.resolveInternalDependency(moduleDir2module: Map<Path, PotatoModule>): DefaultScopedNotation? =
    when (this) {
        is ExternalMavenDependency -> MavenDependency(
            // TODO Report absence of coordinates.
            coordinates,
            scope.compile,
            scope.runtime,
            exported,
        )

        is InternalDependency -> path?.let { path ->
            DefaultPotatoModuleDependency(
                // TODO Report to error module.
                moduleDir2module[path] ?: run {
                    NotResolvedModule(path.name)
                },
                path,
                scope.compile,
                scope.runtime,
                exported,
            )
        }

        is CatalogDependency -> error("Catalog dependency must be processed earlier!")

        else -> error("Unknown dependency type: ${this::class}")
    }?.withTraceFrom(this)
