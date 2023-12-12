/*
 * Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.aomBuilder

import org.jetbrains.amper.core.Result
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
import org.jetbrains.amper.frontend.api.ValueBase
import org.jetbrains.amper.frontend.processing.readTemplatesAndMerge
import org.jetbrains.amper.frontend.schema.Dependency
import org.jetbrains.amper.frontend.schema.ExternalMavenDependency
import org.jetbrains.amper.frontend.schema.InternalDependency
import org.jetbrains.amper.frontend.schema.Modifiers
import org.jetbrains.amper.frontend.schema.Module
import org.jetbrains.amper.frontend.schemaConverter.convertModule
import java.nio.file.Path
import kotlin.io.path.name

class SchemaBasedModelImport : ModelInit {
    override val name = "yaml-schema-based"

    context(ProblemReporterContext)
    override fun getModel(root: Path): Result<Model> {

        // TODO Replace default reader by something other.
        fun readAndPreprocess(moduleFile: Path): Module = convertModule(moduleFile)
            .readTemplatesAndMerge()

        // Find all module files, parse them and perform preprocessing (templates, TODO catalogs)
        val path2SchemaModule = root.findAmperModuleFiles()
            .associateWith { readAndPreprocess(it) }

        // Build AOM from ISM.
        val resultModules = path2SchemaModule.buildAom()

        return DefaultModel(resultModules).asAmperSuccess()
    }
}

/**
 * Build and resolve internal module dependencies.
 */
fun Map<Path, Module>.buildAom(): List<PotatoModule> {
    val modules = map { (mPath, module) ->
        val convertedType = ProductType[module.product.value.type.value.name]!!
        Triple(mPath, module, DefaultModule(mPath.name, convertedType, PotatoModuleFileSource(mPath), module))
    }

    val module2Path = modules.associate { (path, _, module) -> path to module }

    modules.forEach { (_, schemaModule, module) ->
        val dependencies = schemaModule.dependencies.simplifyModifiers().entries
            .associate { (modifiers, unresolved) -> modifiers to unresolved.resolveInternalDependencies(module2Path) }
        val testDependencies = schemaModule.`test-dependencies`.simplifyModifiers().entries
            .associate { (modifiers, unresolved) -> modifiers to unresolved.resolveInternalDependencies(module2Path) }

        val seeds = schemaModule.buildFragmentSeeds()

        val moduleFragments = createFragments(seeds, dependencies, testDependencies)
        val (leaves, testLeaves) = moduleFragments.filterIsInstance<DefaultLeafFragment>().partition { !it.isTest }

        module.apply {
            fragments = moduleFragments
            artifacts = createArtifacts(false, module.type, leaves) +
                    createArtifacts(true, module.type, testLeaves)
            parts = TODO()
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
    override val compile: Boolean = true,
    override val runtime: Boolean = true,
    override val exported: Boolean = false,
) : PotatoModuleDependency, DefaultScopedNotation {
    override val Model.module get() = myModule.asAmperSuccess()
}

/**
 * Resolve internal modules against known ones by path.
 */
private fun Collection<Dependency>.resolveInternalDependencies(modules: Map<Path, DefaultModule>) = mapNotNull {
    when (it) {
        is ExternalMavenDependency -> MavenDependency(
            it.coordinates.value,
            it.`compile-only`.value && !it.`runtime-only`.value,
            !it.`compile-only`.value && it.`runtime-only`.value,
            it.exported.value,
        )

        is InternalDependency -> DefaultPotatoModuleDependency(
            // TODO Report on unresolved module.
            modules[it.path.value] ?: return@mapNotNull null,
            it.`compile-only`.value && !it.`runtime-only`.value,
            !it.`compile-only`.value && it.`runtime-only`.value,
            it.exported.value,
        )
    }
}