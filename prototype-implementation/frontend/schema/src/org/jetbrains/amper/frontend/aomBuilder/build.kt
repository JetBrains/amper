/*
 * Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.aomBuilder

import org.jetbrains.amper.core.Result
import org.jetbrains.amper.core.amperFailure
import org.jetbrains.amper.core.asAmperSuccess
import org.jetbrains.amper.core.messages.ProblemReporterContext
import org.jetbrains.amper.frontend.DefaultScopedNotation
import org.jetbrains.amper.frontend.MavenDependency
import org.jetbrains.amper.frontend.Model
import org.jetbrains.amper.frontend.ModelInit
import org.jetbrains.amper.frontend.PotatoModule
import org.jetbrains.amper.frontend.PotatoModuleDependency
import org.jetbrains.amper.frontend.PotatoModuleFileSource
import org.jetbrains.amper.frontend.ProductType
import org.jetbrains.amper.frontend.ReaderCtx
import org.jetbrains.amper.frontend.processing.readTemplatesAndMerge
import org.jetbrains.amper.frontend.processing.replaceCatalogDependencies
import org.jetbrains.amper.frontend.processing.validateSchema
import org.jetbrains.amper.frontend.schema.CatalogDependency
import org.jetbrains.amper.frontend.schema.Dependency
import org.jetbrains.amper.frontend.schema.ExternalMavenDependency
import org.jetbrains.amper.frontend.schema.InternalDependency
import org.jetbrains.amper.frontend.schema.Module
import org.jetbrains.amper.frontend.schemaConverter.ConvertCtx
import org.jetbrains.amper.frontend.schemaConverter.convertModule
import java.io.Reader
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.name
import kotlin.io.path.pathString
import kotlin.io.path.reader

class SchemaBasedModelImport : ModelInit {
    override val name = "yaml-schema-based"

    context(ProblemReporterContext)
    override fun getModel(root: Path): Result<Model> {

        // TODO Replace default reader by something other.
        // TODO Report non existing file.
        val path2Reader: (Path) -> Reader? = {
            it.takeIf { it.exists() }?.reader()
        }

        fun readAndPreprocess(moduleFile: Path): Module = with(ReaderCtx(path2Reader)) {
            with(ConvertCtx(moduleFile.parent)) {
                convertModule { moduleFile.reader() }
                    .readTemplatesAndMerge()
                    .replaceCatalogDependencies()
                    .validateSchema()
            }
        }

        // Find all module files, parse them and perform preprocessing (templates, TODO catalogs)
        val path2SchemaModule = root.findAmperModuleFiles()
            .associateWith { readAndPreprocess(it) }

        // Fail fast if we have fatal errors.
        if (problemReporter.hasFatal) return amperFailure()

        // Build AOM from ISM.
        val resultModules = path2SchemaModule.buildAom()

        // Propagate parts from fragment to fragment.
        val resolvedParts = DefaultModel(resultModules).resolved

        return resolvedParts.asAmperSuccess()
    }
}

/**
 * Build and resolve internal module dependencies.
 */
context(ProblemReporterContext)
internal fun Map<Path, Module>.buildAom(): List<PotatoModule> {
    val modules = map { (mPath, module) ->
        // TODO Remove duplicating enums.
        val convertedType = ProductType.getValue(module.product.value.type.value.schemaValue)
        Triple(mPath, module, DefaultModule(mPath.name, convertedType, PotatoModuleFileSource(mPath), module))
    }

    val module2Path = modules.associate { (path, _, module) -> path to module }

    modules.forEach { (_, schemaModule, module) ->
        val seeds = schemaModule.buildFragmentSeeds()
        val moduleFragments = createFragments(seeds) { it.resolveInternalDependency(module2Path) }
        val (leaves, testLeaves) = moduleFragments.filterIsInstance<DefaultLeafFragment>().partition { !it.isTest }

        module.apply {
            fragments = moduleFragments
            artifacts = createArtifacts(false, module.type, leaves) +
                    createArtifacts(true, module.type, testLeaves)
        }
    }

    return modules.map { it.third }
}

private fun createArtifacts(
    isTest: Boolean,
    productType: ProductType,
    fragments: List<DefaultLeafFragment>
): List<DefaultArtifact> = when (productType) {
    ProductType.LIB -> listOf(DefaultArtifact(if (!isTest) "lib" else "testLib", fragments, isTest))
    else -> fragments.map { DefaultArtifact(it.name, listOf(it), isTest) }
}

private fun <T> ValueBase<Map<Modifiers, List<T>>>.simplifyModifiers() =
    value.entries.associate { it.key.map { it.value }.toSet() to it.value }

class DefaultPotatoModuleDependency(
    private val myModule: DefaultModule,
    val path: Path,
    override val compile: Boolean = true,
    override val runtime: Boolean = true,
    override val exported: Boolean = false,
) : PotatoModuleDependency, DefaultScopedNotation {
    override val Model.module get() = myModule.asAmperSuccess()
    override fun toString(): String {
        return "InternalDependency(module=${path.pathString})"
    }
}

/**
 * Resolve internal modules against known ones by path.
 */
context(ProblemReporterContext)
private fun Dependency.resolveInternalDependency(modules: Map<Path, DefaultModule>) = let resolve@{
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
                modules[path] ?: NotResolvedModule(path.name),
                path,
                scope.value.compile,
                scope.value.runtime,
                it.exported.value,
            )
        } ?: return@resolve null

        is CatalogDependency -> error("Catalog dependency must be processed earlier!")
    }
}