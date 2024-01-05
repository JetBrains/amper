/*
 * Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.aomBuilder

import org.jetbrains.amper.core.asAmperSuccess
import org.jetbrains.amper.core.messages.ProblemReporterContext
import org.jetbrains.amper.core.system.DefaultSystemInfo
import org.jetbrains.amper.core.system.SystemInfo
import org.jetbrains.amper.frontend.*
import org.jetbrains.amper.frontend.ProductType
import org.jetbrains.amper.frontend.processing.*
import org.jetbrains.amper.frontend.schema.*
import org.jetbrains.amper.frontend.schemaConverter.ConvertCtx
import org.jetbrains.amper.frontend.schemaConverter.convertModule
import java.nio.file.Path
import kotlin.io.path.name
import kotlin.io.path.pathString

/**
 * AOM build function, introduced for testing.
 */
context(ProblemReporterContext)
internal fun doBuild(
    pathResolver: FrontendPathResolver,
    fioCtx: FioContext,
    systemInfo: SystemInfo = DefaultSystemInfo,
): List<PotatoModule>? {
    fun readAndPreprocess(moduleFile: Path, catalog: VersionCatalog): Module? = with(pathResolver) {
        with(ConvertCtx(moduleFile.parent, pathResolver)) {
            with(systemInfo) {
                convertModule(modulePath = moduleFile)
                    ?.readTemplatesAndMerge()
                    ?.replaceCatalogDependencies(catalog)
                    ?.validateSchema()
                    ?.replaceComposeOsSpecific()
                    ?.addKotlinSerialization()
            }
        }
    }

    // Parse all module files and perform preprocessing (templates, catalogs, etc.)
    val path2SchemaModule = fioCtx.amperModuleFiles
        .mapNotNull { moduleFile ->
            val catalogs = fioCtx.amperFiles2gradleCatalogs[moduleFile].orEmpty()
                .mapNotNull { parseGradleVersionCatalog(it) } + BuiltInCatalog
            readAndPreprocess(moduleFile, CompositeVersionCatalog(catalogs))
                ?.let { moduleFile to it }
        }
        .toMap()

    // Fail fast if we have fatal errors.
    if (problemReporter.hasFatal) return null

    // Build AOM from ISM.
    return path2SchemaModule.buildAom(fioCtx.gradleModules)
}

data class ModuleTriple(
    val buildFile: Path,
    val schemaModule: Module,
    val module: DefaultModule,
)

/**
 * Build and resolve internal module dependencies.
 */
context(ProblemReporterContext)
internal fun Map<Path, Module>.buildAom(
    gradleModules: Map<Path, PotatoModule>,
): List<PotatoModule> {
    val modules = map { (mPath, module) ->
        // TODO Remove duplicating enums.
        val convertedType = ProductType.getValue(module.product.value.type.value.schemaValue)
        ModuleTriple(mPath, module, DefaultModule(mPath.parent.name, convertedType, PotatoModuleFileSource(mPath), module))
    }

    val moduleDir2module = modules
        .associate { (path, _, module) -> path.parent to module } + gradleModules

    modules.forEach { (_, schemaModule, module) ->
        val seeds = schemaModule.buildFragmentSeeds()
        val moduleFragments = createFragments(seeds) { it.resolveInternalDependency(moduleDir2module) }
        val (leaves, testLeaves) = moduleFragments.filterIsInstance<DefaultLeafFragment>().partition { !it.isTest }

        module.apply {
            fragments = moduleFragments
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
    private val myModule: PotatoModule,
    val path: Path,
    override val compile: Boolean = true,
    override val runtime: Boolean = true,
    override val exported: Boolean = false,
) : PotatoModuleDependency, DefaultScopedNotation {
    context (ProblemReporterContext)
    override val Model.module get() = myModule.asAmperSuccess()
    override fun toString(): String {
        return "InternalDependency(module=${path.pathString})"
    }
}

/**
 * Resolve internal modules against known ones by path.
 */
context(ProblemReporterContext)
private fun Dependency.resolveInternalDependency(moduleDir2module: Map<Path, PotatoModule>) = let resolve@{
    when (it) {
        is ExternalMavenDependency -> MavenDependency(
            // TODO Report absence of coordinates.
            it.coordinates.value,
            scope.value.compile,
            scope.value.runtime,
            it.exported.value,
        )

        is InternalDependency -> it.path.value?.let { path ->
            DefaultPotatoModuleDependency(
                // TODO Report to error module.
                moduleDir2module[path] ?: run {
                    println(path.pathString + " -- " + moduleDir2module.keys.joinToString { it.pathString })
                    NotResolvedModule(path.name)
                },
                path,
                scope.value.compile,
                scope.value.runtime,
                it.exported.value,
            )
        } ?: return@resolve null

        is CatalogDependency -> error("Catalog dependency must be processed earlier!")
    }
}