/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.aomBuilder

import com.intellij.openapi.vfs.VirtualFile
import kotlinx.serialization.json.Json
import org.jetbrains.amper.frontend.AmperModule
import org.jetbrains.amper.frontend.TaskName
import org.jetbrains.amper.frontend.api.DefaultTrace
import org.jetbrains.amper.frontend.contexts.EmptyContexts
import org.jetbrains.amper.frontend.plugins.PluginYamlRoot
import org.jetbrains.amper.frontend.plugins.Task
import org.jetbrains.amper.frontend.plugins.TaskFromPluginDescription
import org.jetbrains.amper.frontend.project.AmperProjectContext
import org.jetbrains.amper.frontend.project.getTaskOutputRoot
import org.jetbrains.amper.frontend.project.pluginInternalSchemaDirectory
import org.jetbrains.amper.frontend.tree.MapLikeValue
import org.jetbrains.amper.frontend.tree.Refined
import org.jetbrains.amper.frontend.tree.TreeRefiner
import org.jetbrains.amper.frontend.tree.TreeValue
import org.jetbrains.amper.frontend.tree.appendDefaultValues
import org.jetbrains.amper.frontend.tree.reading.readTree
import org.jetbrains.amper.frontend.tree.resolveReferences
import org.jetbrains.amper.frontend.tree.scalarValue
import org.jetbrains.amper.frontend.tree.single
import org.jetbrains.amper.frontend.tree.syntheticBuilder
import org.jetbrains.amper.frontend.types.getDeclaration
import org.jetbrains.amper.plugins.schema.model.PluginData
import java.nio.file.Path
import kotlin.io.path.div
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.readText

fun AmperProjectContext.loadPreparedPluginData(): List<PluginData> {
    return if (pluginDependencies.isNotEmpty()) {
        pluginInternalSchemaDirectory.takeIf { it.isDirectory() }
            ?.listDirectoryEntries("*.json")
            ?.map { Json.decodeFromString<PluginData>(it.readText()) }
            .orEmpty()
    } else emptyList()
}

internal fun BuildCtx.buildPlugins(
    pluginData: List<PluginData>,
    projectContext: AmperProjectContext,
    result: List<ModuleBuildCtx>,
) {
    val plugins = buildList {
        for (pluginData in pluginData) {
            val pluginModuleRoot = projectContext.projectRootDir.toNioPath() / pluginData.pluginModuleRoot

            val pluginModule = result.first {
                it.moduleFile.parent.toNioPath() == pluginModuleRoot
            }
            val pluginFile = pluginModuleRoot / "plugin.yaml"
            if (!pluginFile.isRegularFile()) {
                // We assume for now that missing plugin.yaml is a valid scenario
                // TODO: Maybe at least report it?
                continue
            }

            this += PluginTreeReader(
                projectContext = projectContext,
                pluginId = pluginData.id,
                pluginFile = projectContext.frontendPathResolver.loadVirtualFile(pluginFile),
                pluginModule = pluginModule.module,
                parentBuildCtx = this@buildPlugins,
            )
        }
    }

    for (moduleBuildCtx in result) for (plugin in plugins) {
        val appliedPlugin: PluginYamlRoot = plugin.asAppliedTo(
            module = moduleBuildCtx,
        ) ?: continue
        for ((name, task) in appliedPlugin.tasks) {
            val outputsToMarks = task.action.outputPropertyNames
                .mapNotNull { task.action[it] as Path? }
                .associateWith { path ->
                    task.markOutputsAs.find { it.path == path }
                }.mapValues { (_, mark) ->
                    mark ?: return@mapValues null
                    TaskFromPluginDescription.OutputMark(
                        kind = mark.kind,
                        associateWith = moduleBuildCtx.module.fragments.first {
                            it.isTest == mark.fragment.isTest && it.modifier == mark.fragment.modifier
                        }
                    )
                }
            moduleBuildCtx.module.tasksFromPlugins += DefaultTaskFromPluginDescription(
                name = pluginTaskNameFor(moduleBuildCtx.module, plugin.pluginId, name),
                actionFunctionJvmName = task.action.jvmFunctionName,
                actionClassJvmName = task.action.jvmOwnerClassName,
                actionArguments = task.action.valueHolders.mapValues { (_, v) -> v.value },
                explicitDependsOn = task.dependsOnSideEffectsOf,
                inputs = task.action.inputPropertyNames.mapNotNull { task.action[it] as Path? },
                outputs = outputsToMarks,
                codeSource = plugin.pluginModule,
            )
        }
    }
}

private class PluginTreeReader(
    private val projectContext: AmperProjectContext,
    val pluginModule: AmperModule,
    val pluginId: PluginData.Id,
    pluginFile: VirtualFile,
    parentBuildCtx: BuildCtx,
) {
    private val treeRefiner = TreeRefiner()
    private val buildCtx = parentBuildCtx.copy(
        types = parentBuildCtx.types.getPluginContext(pluginId),
    )

    private val pluginTree: MapLikeValue<Refined> = with(buildCtx) {
        val declaration = buildCtx.types.getDeclaration<PluginYamlRoot>()

        val tree = readTree(
            file = pluginFile,
            type = declaration,
            reportUnknowns = true,
            parseReferences = true,
        )

        treeMerger.mergeTrees(tree)
            .appendDefaultValues()
            .let { treeRefiner.refineTree(it, EmptyContexts) } as MapLikeValue<Refined>
    }

    fun asAppliedTo(
        module: ModuleBuildCtx,
    ): PluginYamlRoot? = with(buildCtx) {
        val moduleRootDir = module.module.source.moduleDir ?: return null
        val pluginConfiguration = module.commonTree.asMapLikeAndGet("settings")?.asMapLikeAndGet(pluginId.value)

        val enabled = pluginConfiguration?.asMapLikeAndGet("enabled")?.scalarValue<Boolean>()
        if (enabled != true) return null

        val taskDirs = (pluginTree.asMapLikeAndGet("tasks") as? Refined)
            ?.refinedChildren
            ?.mapValues { (name, _) ->
                projectContext.getTaskOutputRoot(pluginTaskNameFor(module.module, pluginId, name))
            }.orEmpty()

        // Build a tree with computed "reference-only" values.
        val referenceValuesTree = syntheticBuilder(buildCtx.types, DefaultTrace) {
            `object`<PluginYamlRoot> {
                "module" setTo map {
                    "configuration" setTo pluginConfiguration
                    "rootDir" setTo scalar(moduleRootDir)
                }
                PluginYamlRoot::tasks setTo map {
                    for ((taskName, taskBuildRoot) in taskDirs) {
                        taskName setTo `object`<Task> {
                            "taskDir" setTo scalar(taskBuildRoot)
                        }
                    }
                }
            }
        }

        treeMerger.mergeTrees(listOfNotNull(pluginTree, referenceValuesTree))
            .resolveReferences()
            // TODO: filter-out reference-only values? So far they don't cause any trouble
            .let { treeRefiner.refineTree(it, EmptyContexts) }
            .let { createSchemaNode<PluginYamlRoot>(it) }
    }

    private fun TreeValue<Refined>.asMapLikeAndGet(property: String): TreeValue<Refined>? {
        return (this as? Refined)?.single(property)?.value
    }
}

private fun pluginTaskNameFor(module: AmperModule, pluginId: PluginData.Id, name: String) =
    TaskName.moduleTask(module, "$name@${pluginId.value}")