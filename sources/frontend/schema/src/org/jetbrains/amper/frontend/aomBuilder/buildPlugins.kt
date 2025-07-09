/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.aomBuilder

import com.intellij.openapi.vfs.VirtualFile
import kotlinx.serialization.json.Json
import org.jetbrains.amper.plugins.schema.model.PluginData
import org.jetbrains.amper.frontend.AmperModule
import org.jetbrains.amper.frontend.TaskName
import org.jetbrains.amper.frontend.api.DefaultTrace
import org.jetbrains.amper.frontend.contexts.EmptyContexts
import org.jetbrains.amper.frontend.project.AmperProjectContext
import org.jetbrains.amper.frontend.project.pluginInternalSchemaDirectory
import org.jetbrains.amper.frontend.plugins.PluginYamlRoot
import org.jetbrains.amper.frontend.plugins.Task
import org.jetbrains.amper.frontend.project.getTaskOutputRoot
import org.jetbrains.amper.frontend.tree.ListValue
import org.jetbrains.amper.frontend.tree.MapLikeValue
import org.jetbrains.amper.frontend.tree.NoValue
import org.jetbrains.amper.frontend.tree.Owned
import org.jetbrains.amper.frontend.tree.OwnedTree
import org.jetbrains.amper.frontend.tree.ReferenceValue
import org.jetbrains.amper.frontend.tree.Refined
import org.jetbrains.amper.frontend.tree.RefinedTree
import org.jetbrains.amper.frontend.tree.ScalarValue
import org.jetbrains.amper.frontend.tree.TreeRefiner
import org.jetbrains.amper.frontend.tree.TreeValue
import org.jetbrains.amper.frontend.tree.TreeVisitor
import org.jetbrains.amper.frontend.tree.appendDefaultValues
import org.jetbrains.amper.frontend.tree.reading.readTree
import org.jetbrains.amper.frontend.tree.resolveReferences
import org.jetbrains.amper.frontend.tree.single
import org.jetbrains.amper.frontend.tree.syntheticBuilder
import org.jetbrains.amper.frontend.types.getDeclaration
import java.nio.file.Path
import kotlin.collections.iterator
import kotlin.collections.orEmpty
import kotlin.io.path.Path
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
            val pluginModuleRoot = projectContext.projectRootDir.toNioPath() / pluginData.pluginModuleRoot.let(::Path)

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
            moduleBuildCtx.module.tasksFromPlugins += DefaultTaskFromPluginDescription(
                name = pluginTaskNameFor(moduleBuildCtx.module, plugin.pluginId, name),
                actionFunctionJvmName = task.action.jvmFunctionName,
                actionClassJvmName =  task.action.jvmOwnerClassName,
                actionArguments = task.action.valueHolders.mapValues { (_, v) -> v.value },
                explicitDependsOn = task.dependsOnSideEffectsOf,
                inputs = task.action.inputPropertyNames.mapNotNull { task.action[it] as Path? },
                outputs = task.action.outputPropertyNames.mapNotNull { task.action[it] as Path? },
                codeSource = plugin.pluginModule,
                pluginId = plugin.pluginId.value,
                outputMarks = task.markOutputsAs.map { generates ->
                    DefaultTaskFromPluginDescription.OutputMark(
                        path = generates.path,
                        kind = generates.kind,
                        associateWith = moduleBuildCtx.module.fragments.first {
                            it.isTest == generates.fragment.isTest &&
                                    it.modifier == generates.fragment.modifier
                        },
                    )
                }
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
    val buildCtx = parentBuildCtx.copy(
        types = parentBuildCtx.types.getPluginContext(pluginId),
    )

    private val pluginTree: MapLikeValue<Refined> = with(buildCtx) {
        val declaration = buildCtx.types.getDeclaration<PluginYamlRoot>()

        val tree = readTree(
            file = pluginFile,
            type = declaration,
            reportUnknowns = true,
            parseReferences = true,
        ) ?: throw error("Unable to read the plugin `$pluginId` from ${pluginFile.path}")

        treeMerger.mergeTrees(tree)
            .appendDefaultValues()
            .let { treeRefiner.refineTree(it, EmptyContexts) } as MapLikeValue<Refined>
    }

    fun asAppliedTo(
        module: ModuleBuildCtx,
    ): PluginYamlRoot? = with(buildCtx) {
        val moduleRootDir = module.module.source.moduleDir ?: return null
        val pluginConfiguration = module.commonTree.asMapLikeAndGet("settings")?.asMapLikeAndGet(pluginId.value)

        val enabled = pluginConfiguration?.asMapLikeAndGet("enabled") ?: return null
        if ((enabled as ScalarValue<*>).value != true)
            return null

        val taskDirs = (pluginTree.asMapLikeAndGet("tasks") as? Refined)
            ?.refinedChildren
            ?.mapValues { (name, _) ->
                projectContext.getTaskOutputRoot(pluginTaskNameFor(module.module, pluginId, name))
            }.orEmpty()

        // Build a tree with computed "reference-only" values.
        val referenceValuesTree = syntheticBuilder(buildCtx.types, DefaultTrace) {
            mapLike<PluginYamlRoot> {
                "module" setTo map {
                    put("configuration", pluginConfiguration.convertToOwned())
                    put("rootDir", scalar(moduleRootDir))
                }
                PluginYamlRoot::tasks setTo map {
                    for ((taskName, taskBuildRoot) in taskDirs) {
                        put(taskName, mapLike<Task> {
                            "taskDir" setTo scalar(taskBuildRoot)
                        })
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

@Suppress("UNCHECKED_CAST")
private fun RefinedTree.convertToOwned(): OwnedTree {
    val visitor = object : TreeVisitor<OwnedTree, Refined> {
        override fun visitScalarValue(value: ScalarValue<Refined>) = value as ScalarValue<Owned>
        override fun visitNoValue(value: NoValue) = value as OwnedTree
        override fun visitReferenceValue(value: ReferenceValue<Refined>) = value as OwnedTree
        override fun visitListValue(value: ListValue<Refined>) = ListValue(
            children = value.children.map { it.convertToOwned() },
            trace = value.trace,
            contexts = value.contexts,
        )
        override fun visitMapValue(value: MapLikeValue<Refined>) = Owned(
            children = value.children.map { MapLikeValue.Property(
                key = it.key,
                kTrace = it.kTrace,
                value = it.value.convertToOwned(),
                pType = it.pType,
            )
            },
            type = value.type,
            trace = value.trace,
            contexts = value.contexts,
        )
    }
    return visitor.visitValue(this)
}

private fun pluginTaskNameFor(module: AmperModule, pluginId: PluginData.Id, name: String) =
    TaskName.moduleTask(module, "$name@${pluginId.value}")