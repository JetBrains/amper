/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.aomBuilder.plugins

import com.intellij.openapi.vfs.VirtualFile
import kotlinx.serialization.json.Json
import org.jetbrains.amper.frontend.AmperModule
import org.jetbrains.amper.frontend.SchemaBundle
import org.jetbrains.amper.frontend.TaskName
import org.jetbrains.amper.frontend.aomBuilder.BuildCtx
import org.jetbrains.amper.frontend.aomBuilder.DefaultTaskFromPluginDescription
import org.jetbrains.amper.frontend.aomBuilder.ModuleBuildCtx
import org.jetbrains.amper.frontend.aomBuilder.createSchemaNode
import org.jetbrains.amper.frontend.api.DefaultTrace
import org.jetbrains.amper.frontend.api.SchemaNode
import org.jetbrains.amper.frontend.api.TraceableString
import org.jetbrains.amper.frontend.asBuildProblemSource
import org.jetbrains.amper.frontend.contexts.EmptyContexts
import org.jetbrains.amper.frontend.plugins.ExtensionSchemaNode
import org.jetbrains.amper.frontend.plugins.PluginYamlRoot
import org.jetbrains.amper.frontend.plugins.Task
import org.jetbrains.amper.frontend.plugins.TaskFromPluginDescription
import org.jetbrains.amper.frontend.project.AmperProjectContext
import org.jetbrains.amper.frontend.project.getTaskOutputRoot
import org.jetbrains.amper.frontend.project.pluginInternalDataFile
import org.jetbrains.amper.frontend.reportBundleError
import org.jetbrains.amper.frontend.schema.ProductType
import org.jetbrains.amper.frontend.tree.MapLikeValue
import org.jetbrains.amper.frontend.tree.Refined
import org.jetbrains.amper.frontend.tree.TreeRefiner
import org.jetbrains.amper.frontend.tree.TreeValue
import org.jetbrains.amper.frontend.tree.appendDefaultValues
import org.jetbrains.amper.frontend.tree.asMapLike
import org.jetbrains.amper.frontend.tree.get
import org.jetbrains.amper.frontend.tree.reading.readTree
import org.jetbrains.amper.frontend.tree.resolveReferences
import org.jetbrains.amper.frontend.tree.scalarValue
import org.jetbrains.amper.frontend.tree.single
import org.jetbrains.amper.frontend.tree.syntheticBuilder
import org.jetbrains.amper.frontend.types.PluginYamlTypingContext
import org.jetbrains.amper.frontend.types.SchemaObjectDeclaration
import org.jetbrains.amper.frontend.types.SchemaObjectDeclarationBase
import org.jetbrains.amper.frontend.types.SchemaOrigin
import org.jetbrains.amper.frontend.types.SchemaType
import org.jetbrains.amper.frontend.types.getDeclaration
import org.jetbrains.amper.frontend.types.toType
import org.jetbrains.amper.plugins.schema.model.PluginData
import org.jetbrains.amper.problems.reporting.BuildProblemType
import org.jetbrains.amper.problems.reporting.FileBuildProblemSource
import org.jetbrains.amper.problems.reporting.MultipleLocationsBuildProblemSource
import org.jetbrains.amper.stdlib.collections.distinctBy
import kotlin.io.path.div
import kotlin.io.path.exists
import kotlin.io.path.isRegularFile
import kotlin.io.path.readText

fun AmperProjectContext.loadPreparedPluginData(): List<PluginData> {
    return if (pluginModuleFiles.isNotEmpty() && pluginInternalDataFile.exists()) {
        Json.decodeFromString<List<PluginData>>(pluginInternalDataFile.readText())
    } else emptyList()
}

internal fun BuildCtx.buildPlugins(
    pluginData: List<PluginData>,
    projectContext: AmperProjectContext,
    result: List<ModuleBuildCtx>,
) {
    val seenPluginIds = hashMapOf<String, MutableList<TraceableString>>()
    val plugins = projectContext.pluginModuleFiles.mapNotNull mapPlugins@ { pluginModuleFile ->
        val pluginModule = result.find { it.moduleFile == pluginModuleFile }
            ?: return@mapPlugins null

        run { // Report invalid product type
            val product = pluginModule.moduleCtxModule.product
            if (product.type != ProductType.JVM_AMPER_PLUGIN) {
                problemReporter.reportBundleError(
                    product.asBuildProblemSource(),
                    "plugin.unexpected.product.type", ProductType.JVM_AMPER_PLUGIN.value, product.type
                )
                return@mapPlugins null
            }
        }

        val pluginId = pluginModule.moduleCtxModule.plugin!!.id // safe - default is always set
        if (pluginId.value in seenPluginIds) {
            seenPluginIds[pluginId.value]!!.add(pluginId)
            return@mapPlugins null // Skip the duplicate
        } else {
            seenPluginIds[pluginId.value] = mutableListOf(pluginId)
        }

        val pluginData = pluginData.find { it.id.value == pluginId.value }
            ?: return@mapPlugins null

        pluginModule.moduleCtxModule.plugin?.schemaExtensionClassName?.let { moduleExtensionSchemaName ->
            if (pluginData.declarations.classes.none { it.name.qualifiedName == moduleExtensionSchemaName.value }) {
                problemReporter.reportBundleError(
                    source = moduleExtensionSchemaName.asBuildProblemSource(),
                    "plugin.missing.schema.class", moduleExtensionSchemaName,
                    problemType = BuildProblemType.UnresolvedReference,
                )
            }
        }

        val pluginFile = run { // Locate plugin.yaml
            val pluginModuleRoot = pluginModule.moduleFile.parent.toNioPath()
            val pluginFile = pluginModuleRoot / "plugin.yaml"
            if (!pluginFile.isRegularFile()) {
                // We assume for now that missing plugin.yaml is a valid scenario
                // TODO: Maybe at least report it?
                return@mapPlugins null
            }
            pluginFile
        }

        PluginTreeReader(
            projectContext = projectContext,
            pluginData = pluginData,
            pluginFile = projectContext.frontendPathResolver.loadVirtualFile(pluginFile),
            pluginModule = pluginModule.module,
            buildCtx = this@buildPlugins,
        )
    }

    // Report duplicate IDs
    for ((id, traceableIds) in seenPluginIds) {
        if (traceableIds.size < 2) continue
        val source = MultipleLocationsBuildProblemSource(
            sources = traceableIds.map { it.asBuildProblemSource() as FileBuildProblemSource },
            groupingMessage = SchemaBundle.message("plugin.id.duplicate.grouping", id)
        )
        problemReporter.reportBundleError(source, "plugin.id.duplicate")
    }

    for (moduleBuildCtx in result) for (plugin in plugins) {
        val appliedPlugin: PluginYamlRoot = plugin.asAppliedTo(
            module = moduleBuildCtx,
        ) ?: continue
        for ((name, task) in appliedPlugin.tasks) {
            val taskInfo = task.action.taskInfo
            val pathsCollector = InputOutputPathsCollector()
            pathsCollector.gatherPaths(task.action)
            val outputMarks = task.markOutputsAs.distinctBy(
                selector = { it.path },
                onDuplicates = { path, duplicateMarks ->
                    val source = MultipleLocationsBuildProblemSource(
                        sources = duplicateMarks.mapNotNull { it.asBuildProblemSource() as? FileBuildProblemSource },
                        groupingMessage = SchemaBundle.message("plugin.invalid.mark.output.as.duplicates.grouping"),
                    )
                    problemReporter.reportBundleError(source, "plugin.invalid.mark.output.as.duplicates", path)
                }
            ).associateBy { it.path }
            outputMarks.forEach { (path, mark) ->
                if (path !in pathsCollector.allOutputPaths) {
                    problemReporter.reportBundleError(
                        mark.asBuildProblemSource(), "plugin.invalid.mark.output.as.no.such.path", path
                    )
                }
            }
            val outputsToMarks = pathsCollector.allOutputPaths.associateWith { path ->
                val mark = outputMarks[path] ?: return@associateWith null
                TaskFromPluginDescription.OutputMark(
                    kind = mark.kind,
                    associateWith = moduleBuildCtx.module.fragments.first {
                        it.isTest == mark.fragment.isTest && it.modifier == mark.fragment.modifier
                    }
                )
            }
            moduleBuildCtx.module.tasksFromPlugins += DefaultTaskFromPluginDescription(
                name = pluginTaskNameFor(moduleBuildCtx.module, plugin.pluginData.id, name),
                actionFunctionJvmName = taskInfo.jvmFunctionName,
                actionClassJvmName = taskInfo.jvmFunctionClassName,
                actionArguments = task.action.valueHolders.mapValues { (_, v) -> v.value },
                explicitDependsOn = task.dependsOnSideEffectsOf,
                inputs = pathsCollector.allInputPaths.toList(),
                outputs = outputsToMarks,
                codeSource = plugin.pluginModule,
                explicitOptOutOfExecutionAvoidance = taskInfo.optOutOfExecutionAvoidance,
            )
        }
    }
}

private class PluginTreeReader(
    private val projectContext: AmperProjectContext,
    val pluginModule: AmperModule,
    val pluginData: PluginData,
    pluginFile: VirtualFile,
    buildCtx: BuildCtx,
) {
    private val treeRefiner = TreeRefiner()

    private val buildCtx = buildCtx.copy(types = PluginYamlTypingContext(buildCtx.types, pluginData))

    private val pluginTree: MapLikeValue<Refined> = with(this.buildCtx) {
        val declaration = types.getDeclaration<PluginYamlRoot>()

        val tree = readTree(
            file = pluginFile,
            declaration = declaration,
            reportUnknowns = true,
            parseReferences = true,
            parseContexts = false,
        )

        val withDefaults = treeMerger.mergeTrees(tree)
            .appendDefaultValues()
        treeRefiner.refineTree(withDefaults, EmptyContexts) as MapLikeValue<Refined>
    }

    fun asAppliedTo(
        module: ModuleBuildCtx,
    ): PluginYamlRoot? = with(this@PluginTreeReader.buildCtx) {
        val moduleRootDir = module.module.source.moduleDir
        val pluginConfiguration = module.commonTree.asMapLikeAndGet("settings")?.asMapLikeAndGet(pluginData.id.value)
            ?.asMapLike ?: return null

        val enabled = pluginConfiguration["enabled"].singleOrNull()?.value?.scalarValue<Boolean>()
        if (enabled != true) return null

        val taskDirs = (pluginTree.asMapLikeAndGet("tasks") as? Refined)
            ?.refinedChildren
            ?.mapValues { (name, _) ->
                projectContext.getTaskOutputRoot(pluginTaskNameFor(module.module, pluginData.id, name))
            }.orEmpty()

        val configurationType = pluginConfiguration.type as SchemaType.ObjectType
        val moduleConfigurationDeclaration = object : SchemaObjectDeclarationBase() {
            override val properties = listOf(
                SchemaObjectDeclaration.Property(
                    "configuration", configurationType, origin = configurationType.declaration.origin, default = null,
                ),
                SchemaObjectDeclaration.Property(
                    "rootDir", SchemaType.PathType, origin = SchemaOrigin.Builtin, default = null,
                ),
            )
            override fun createInstance(): SchemaNode = ExtensionSchemaNode().also {
                it.schemaType = this
            }
            override val qualifiedName get() = "ModuleConfigurationForPlugin"
            override val origin get() = SchemaOrigin.Builtin
        }

        // Build a tree with computed "reference-only" values.
        val referenceValuesTree = syntheticBuilder(this@PluginTreeReader.buildCtx.types, DefaultTrace) {
            `object`<PluginYamlRoot> {
                "module" setTo `object`(moduleConfigurationDeclaration.toType()) {
                    "configuration" setTo pluginConfiguration
                    "rootDir" setTo scalar(moduleRootDir)
                }
                PluginYamlRoot::tasks setToMap {
                    for ((taskName, taskBuildRoot) in taskDirs) {
                        taskName setTo `object`<Task> {
                            "taskDir" setTo scalar(taskBuildRoot)
                        }
                    }
                }
            }
        }

        val mergedTree = treeMerger.mergeTrees(listOfNotNull(pluginTree, referenceValuesTree))
        val refinedTree = treeRefiner.refineTree(mergedTree, EmptyContexts)
        val resolvedTree = refinedTree.resolveReferences()
        createSchemaNode<PluginYamlRoot>(resolvedTree)
    }

    private fun TreeValue<Refined>.asMapLikeAndGet(property: String): TreeValue<Refined>? {
        return (this as? Refined)?.single(property)?.value
    }
}

private fun pluginTaskNameFor(module: AmperModule, pluginId: PluginData.Id, name: String) =
    TaskName.moduleTask(module, "$name@${pluginId.value}")
