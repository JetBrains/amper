/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.aomBuilder

import com.intellij.openapi.util.io.toNioPathOrNull
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.amper.core.messages.BuildProblemImpl
import org.jetbrains.amper.core.messages.BuildProblemSource
import org.jetbrains.amper.core.messages.FileBuildProblemSource
import org.jetbrains.amper.core.messages.Level
import org.jetbrains.amper.core.messages.ProblemReporterContext
import org.jetbrains.amper.core.system.DefaultSystemInfo
import org.jetbrains.amper.core.system.SystemInfo
import org.jetbrains.amper.frontend.AddToModuleRootsFromCustomTask
import org.jetbrains.amper.frontend.CompositeString
import org.jetbrains.amper.frontend.CompositeStringPart
import org.jetbrains.amper.frontend.CustomTaskDescription
import org.jetbrains.amper.frontend.DefaultScopedNotation
import org.jetbrains.amper.frontend.KnownCurrentTaskProperty
import org.jetbrains.amper.frontend.KnownModuleProperty
import org.jetbrains.amper.frontend.MavenDependency
import org.jetbrains.amper.frontend.Platform
import org.jetbrains.amper.frontend.AmperModule
import org.jetbrains.amper.frontend.LocalModuleDependency
import org.jetbrains.amper.frontend.AmperModuleFileSource
import org.jetbrains.amper.frontend.SchemaBundle
import org.jetbrains.amper.frontend.TaskName
import org.jetbrains.amper.frontend.VersionCatalog
import org.jetbrains.amper.frontend.api.Trace
import org.jetbrains.amper.frontend.api.unsafe
import org.jetbrains.amper.frontend.api.withTraceFrom
import org.jetbrains.amper.frontend.catalogs.VersionsCatalogProvider
import org.jetbrains.amper.frontend.customTaskSchema.CustomTaskNode
import org.jetbrains.amper.frontend.customTaskSchema.CustomTaskSourceSetType
import org.jetbrains.amper.frontend.diagnostics.AomSingleModuleDiagnosticFactories
import org.jetbrains.amper.frontend.diagnostics.IsmDiagnosticFactories
import org.jetbrains.amper.frontend.processing.BuiltInCatalog
import org.jetbrains.amper.frontend.processing.CompositeVersionCatalog
import org.jetbrains.amper.frontend.processing.addImplicitDependencies
import org.jetbrains.amper.frontend.processing.parseGradleVersionCatalog
import org.jetbrains.amper.frontend.processing.readTemplatesAndMerge
import org.jetbrains.amper.frontend.processing.replaceCatalogDependencies
import org.jetbrains.amper.frontend.processing.replaceComposeOsSpecific
import org.jetbrains.amper.frontend.project.AmperProjectContext
import org.jetbrains.amper.frontend.project.customTaskName
import org.jetbrains.amper.frontend.reportBundleError
import org.jetbrains.amper.frontend.schema.Base
import org.jetbrains.amper.frontend.schema.CatalogDependency
import org.jetbrains.amper.frontend.schema.Dependency
import org.jetbrains.amper.frontend.schema.ExternalMavenDependency
import org.jetbrains.amper.frontend.schema.InternalDependency
import org.jetbrains.amper.frontend.schema.Module
import org.jetbrains.amper.frontend.schema.ProductType
import org.jetbrains.amper.frontend.schema.noModifiers
import org.jetbrains.amper.frontend.schemaConverter.psi.Converter
import org.jetbrains.amper.frontend.schemaConverter.psi.ConverterImpl
import org.jetbrains.amper.frontend.schemaConverter.psi.asAbsolutePath
import java.nio.file.Path
import kotlin.io.path.name
import kotlin.io.path.pathString

/**
 * Module wrapper to hold also chosen catalog.
 */
private data class ModuleHolder(
    val module: Module,
    val chosenCatalog: VersionCatalog?,
)

/**
 * AOM build function, introduced for testing.
 */
context(ProblemReporterContext)
internal fun doBuild(
    projectContext: AmperProjectContext,
    systemInfo: SystemInfo = DefaultSystemInfo,
): List<AmperModule>? {
    // Parse all module files and perform preprocessing (templates, catalogs, etc.)
    val path2SchemaModule = projectContext.amperModuleFiles
        .mapNotNull { moduleFile ->
            // Read initial module file.
            val converter = ConverterImpl(
                moduleFile.parent,
                projectContext.frontendPathResolver,
                problemReporter
            )
            val nonProcessed = converter.
                // TODO Report when file is not found.
                convertModule(moduleFile)?.readTemplatesAndMerge(projectContext)
            ?: return@mapNotNull null

            // Choose catalogs.
            val chosenCatalog = with(converter) {
                projectContext.tryGetCatalogFor(moduleFile, nonProcessed)
            }

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

    val gradleModules = projectContext.gradleBuildFilesWithoutAmper.map { DumbGradleModule(it) }
    val moduleTriples = path2SchemaModule
        .buildAom(gradleModules)
        .onEach { triple -> triple.module.addImplicitDependencies() }

    val moduleDir2module = moduleTriples
        .associate { (path, _, module) -> path.parent to module }
        .mapKeys { (k, _) -> k.toNioPath() }

    projectContext.amperCustomTaskFiles.mapNotNull { customTaskFile ->
        val moduleTriple = moduleTriples.firstOrNull { it.buildFile.parent == customTaskFile.parent } ?: run {
            problemReporter.reportMessage(BuildProblemImpl(
                buildProblemId = "INVALID_CUSTOM_TASK_FILE_PATH",
                source = object : FileBuildProblemSource {
                    override val file: Path
                        get() = customTaskFile.toNioPath()
                },
                message = "Unable to find module for custom task file: $customTaskFile",
                level = Level.Error,
            ))
            return@mapNotNull null
        }

        val customTask = let {
            val converter = ConverterImpl(moduleTriple.buildFile.parent, projectContext.frontendPathResolver, problemReporter)
            val node = converter.convertCustomTask(customTaskFile)
            if (node == null) {
                problemReporter.reportMessage(BuildProblemImpl(
                    buildProblemId = "INVALID_CUSTOM_TASK",
                    source = object : FileBuildProblemSource {
                        override val file: Path
                            get() = customTaskFile.toNioPath()
                    },
                    message = "Invalid custom task format: $customTaskFile",
                    level = Level.Error,
                ))
                return@mapNotNull null
            }

            val moduleResolver = { path: String ->
                moduleDir2module[
                    with(converter) { path.asAbsolutePath() }
                ]
            }
            buildCustomTask(customTaskFile, node, moduleTriple.module, moduleResolver) ?: return@mapNotNull null
        }

        return@mapNotNull moduleTriple.module to customTask
    }
        .groupBy { it.first }
        .forEach { (defaultModule, list) -> defaultModule.customTasks = list.map { it.second } }

    moduleTriples.onEach { triple ->
        AomSingleModuleDiagnosticFactories.forEach { with(it) { triple.module.analyze() } }
    }

    // Fail fast if we have fatal errors.
    if (problemReporter.hasFatal) return null

    // Build AOM from ISM.
    return moduleTriples.map { it.module } + gradleModules
}

/**
 * Try to find gradle catalog and compose it with built-in catalog.
 */
context(Converter)
internal fun VersionsCatalogProvider.tryGetCatalogFor(file: VirtualFile, nonProcessed: Base): VersionCatalog {
    val gradleCatalog = getCatalogPathFor(file)?.let { pathResolver.parseGradleVersionCatalog(it) }
    val compositeCatalog = addBuiltInCatalog(nonProcessed, gradleCatalog)
    return compositeCatalog
}

/**
 * Try to get used version catalog.
 */
context(ProblemReporterContext)
private fun addBuiltInCatalog(
    nonProcessed: Base,
    otherCatalog: VersionCatalog? = null,
): VersionCatalog {
    val commonSettings = nonProcessed.settings[noModifiers]
    val compose = commonSettings?.compose
    val serialization = commonSettings?.kotlin?.serialization
    val builtInCatalog = BuiltInCatalog(
        serializationVersion = serialization?.version?.takeIf { serialization.enabled },
        composeVersion = compose?.version?.takeIf { compose.enabled },
    )
    val catalogs = otherCatalog?.let { listOf(it) }.orEmpty() + builtInCatalog
    val compositeCatalog = CompositeVersionCatalog(catalogs)
    return compositeCatalog
}

context(ProblemReporterContext)
private fun buildCustomTask(
    virtualFile: VirtualFile,
    node: CustomTaskNode,
    module: AmperModule,
    moduleResolver: (String) -> AmperModule?,
): CustomTaskDescription? {
    val buildProblemSource = object : FileBuildProblemSource {
        override val file: Path
            get() = virtualFile.toNioPath()
    }

    val codeModule = moduleResolver(node.module.pathString)
    if (codeModule == null) {
        problemReporter.reportMessage(BuildProblemImpl(
            buildProblemId = "UNKNOWN_MODULE",
            source = buildProblemSource,
            message = "Unresolved module reference: ${node.module.pathString}",
            level = Level.Error,
        ))
        return null
    }

    return DefaultCustomTaskDescription(
        name = TaskName.moduleTask(module, virtualFile.customTaskName()),
        source = virtualFile.toNioPath(),
        origin = node,
        type = node.type,
        module = module,
        jvmArguments = node.jvmArguments.orEmpty().map { parseStringWithReferences(it, buildProblemSource, moduleResolver) },
        programArguments = node.programArguments.orEmpty().map { parseStringWithReferences(it, buildProblemSource, moduleResolver) },
        environmentVariables = node.environmentVariables.orEmpty().mapValues { parseStringWithReferences(it.value, buildProblemSource, moduleResolver) },
        dependsOn = node.dependsOn.orEmpty().map { TaskName(it) },
        publishArtifacts = node.publishArtifact.orEmpty().map {
            DefaultPublishArtifactFromCustomTask(
                pathWildcard = it.path,
                artifactId = it.artifactId,
                classifier = it.classifier,
                extension = it.extension,
            )
        },
        customTaskCodeModule = codeModule,
        addToModuleRootsFromCustomTask = node.addTaskOutputToSourceSet.orEmpty().mapNotNull {
            val relativePath = it.taskOutputSubFolder.toNioPathOrNull()
            if (relativePath == null) {
                problemReporter.reportMessage(BuildProblemImpl(
                    buildProblemId = "INVALID_TASK_OUTPUT_SUBFOLDER",
                    source = buildProblemSource,
                    message = "'taskOutputSubFolder' property is not a relative path",
                    level = Level.Error,
                ))
                return@mapNotNull null
            }

            DefaultAddToModuleRootsFromCustomTask(
                taskOutputRelativePath = relativePath,
                isTest = it.addToTestSources,
                type = when (it.sourceSet) {
                    CustomTaskSourceSetType.SOURCES -> AddToModuleRootsFromCustomTask.Type.SOURCES
                    CustomTaskSourceSetType.RESOURCES -> AddToModuleRootsFromCustomTask.Type.RESOURCES
                },
                platform = Platform.JVM, // TODO
            )
        },
    )
}

private val propertyReferenceRegex = Regex("\\$\\{(module\\(([./0-9a-zA-Z\\-_]+)\\)\\.)?([0-9a-zA-Z]+)}")
private val unresolvedReferenceRegex1 = Regex("(?<!\\\\)\\$")
private val unresolvedReferenceRegex2 = Regex("\\\\\\\\\\$")

// TODO: This is not a real parser and it won't provide a good IDE support either
// Please decide on an appropriate references syntax and rewrite
context(ProblemReporterContext)
internal fun parseStringWithReferences(
    value: String,
    source: BuildProblemSource,
    moduleResolver: (String) -> AmperModule?,
): CompositeString {
    var pos = 0
    val result = mutableListOf<CompositeStringPart>()

    fun addLiteralPart(part: String) {
        if (unresolvedReferenceRegex1.containsMatchIn(part) || unresolvedReferenceRegex2.containsMatchIn(part)) {
            problemReporter.reportMessage(BuildProblemImpl(
                buildProblemId = "STR_REF_UNRESOLVED_TYPE",
                source = source,
                message = "Contains unresolved reference: $part",
                level = Level.Error,
            ))
        }

        val unescaped = part
            .replace("\\\\", "\u0000")
            .replace("\\$", "$")
            .replace("\u0000", "\\")

        result.add(CompositeStringPart.Literal(unescaped))
    }

    propertyReferenceRegex.findAll(value).forEach { match ->
        val literalPart = value.substring(pos, match.range.first)
        if (literalPart.isNotEmpty()) {
            addLiteralPart(literalPart)
        }
        pos = match.range.last + 1

        val (_, _, modulePath, propertyName) = match.groupValues

        if (modulePath.isEmpty()) {
            // current task property reference
            val knownProperty = KnownCurrentTaskProperty.namesMap[propertyName]
            if (knownProperty == null) {
                problemReporter.reportMessage(BuildProblemImpl(
                    buildProblemId = "STR_REF_UNKNOWN_CURRENT_TASK_PROPERTY",
                    source = source,
                    message = "Unknown current task property '$propertyName': ${match.value}",
                    level = Level.Error,
                ))
                return@forEach
            }

            result.add(CompositeStringPart.CurrentTaskProperty(
                property = knownProperty,
                originalReferenceText = match.value,
            ))
        } else {
            // module property reference
            val knownPropertyName = KnownModuleProperty.namesMap[propertyName]
            if (knownPropertyName == null) {
                problemReporter.reportMessage(BuildProblemImpl(
                    buildProblemId = "STR_REF_UNKNOWN_MODULE_PROPERTY",
                    source = source,
                    message = "Unknown property name '$propertyName': ${match.value}",
                    level = Level.Error,
                ))
                return@forEach
            }

            val resolvedModule = moduleResolver(modulePath)
            if (resolvedModule == null) {
                problemReporter.reportMessage(BuildProblemImpl(
                    buildProblemId = "STR_REF_UNKNOWN_MODULE",
                    source = source,
                    message = "Unknown module '$modulePath' referenced from '${match.value}'",
                    level = Level.Error,
                ))
                return@forEach
            }

            result.add(CompositeStringPart.ModulePropertyReference(
                referencedModule = resolvedModule,
                property = knownPropertyName,
                originalReferenceText = match.value,
            ))
        }
    }

    if (pos < value.length) {
        addLiteralPart(value.substring(pos))
    }

    return CompositeString(parts = result)
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
private fun Map<VirtualFile, ModuleHolder>.buildAom(gradleModules: List<DumbGradleModule>): List<ModuleTriple> {
    val modules = mapNotNull { (mPath, holder) ->
        val noProduct = holder.module::product.unsafe == null
        if (noProduct || holder.module.product::type.unsafe == null) {
            if (noProduct) {
                reportEmptyModule(mPath)
            }
            else {
                SchemaBundle.reportBundleError(
                    property = holder.module::product,
                    messageKey = "product.not.defined",
                    level = Level.Fatal,
                )
            }
            return@mapNotNull null
        }
        // TODO Remove duplicating enums.
        ModuleTriple(
            buildFile = mPath,
            schemaModule = holder.module,
            module = DefaultModule(
                userReadableName = mPath.parent.name,
                type = holder.module.product.type,
                source = AmperModuleFileSource(mPath.toNioPath()),
                origin = holder.module,
                usedCatalog = holder.chosenCatalog,
            )
        )
    }

    val moduleDirToAmperModule = modules.associate { (path, _, module) -> path.parent.toNioPath() to module }
    val moduleDirToGradleModule = gradleModules.associateBy { it.gradleBuildFile.parent.toNioPath() }
    val moduleDirToModule = moduleDirToAmperModule + moduleDirToGradleModule

    modules.forEach { (modulePath, schemaModule, module) ->
        val seeds = schemaModule.buildFragmentSeeds()
        val moduleFragments = createFragments(seeds, modulePath, module) { it.resolveInternalDependency(moduleDirToModule) }
        val propagatedFragments = moduleFragments.withPropagatedSettings()
        val (leaves, testLeaves) = moduleFragments.filterIsInstance<DefaultLeafFragment>().partition { !it.isTest }

        module.apply {
            fragments = propagatedFragments
            artifacts = createArtifacts(false, module.type, leaves) +
                    createArtifacts(true, module.type, testLeaves)
        }
    }

    return modules
}

internal fun ProblemReporterContext.reportEmptyModule(mPath: VirtualFile) {
    problemReporter.reportMessage(
        BuildProblemImpl(
            "product.not.defined",
            object : FileBuildProblemSource {
                override val file: Path = mPath.toNioPath()
            },
            SchemaBundle.message("product.not.defined.empty"),
            Level.Fatal
        )
    )
}

private fun createArtifacts(
    isTest: Boolean,
    productType: ProductType,
    fragments: List<DefaultLeafFragment>
): List<DefaultArtifact> = when (productType) {
    ProductType.LIB -> listOf(DefaultArtifact(if (!isTest) "lib" else "testLib", fragments, isTest))
    else -> fragments.map { DefaultArtifact(it.name, listOf(it), isTest) }
}

class DefaultLocalModuleDependency(
    override val module: AmperModule,
    val path: Path,
    override val compile: Boolean = true,
    override val runtime: Boolean = true,
    override val exported: Boolean = false,
) : LocalModuleDependency, DefaultScopedNotation {
    override var trace: Trace? = null

    override fun toString(): String {
        return "InternalDependency(module=${path.pathString})"
    }
}

/**
 * Resolve internal modules against known ones by path.
 */
context(ProblemReporterContext)
private fun Dependency.resolveInternalDependency(moduleDir2module: Map<Path, AmperModule>): DefaultScopedNotation? =
    when (this) {
        is ExternalMavenDependency -> MavenDependency(
            // TODO Report absence of coordinates.
            coordinates,
            scope.compile,
            scope.runtime,
            exported,
        )

        is InternalDependency -> path?.let { path ->
            DefaultLocalModuleDependency(
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
