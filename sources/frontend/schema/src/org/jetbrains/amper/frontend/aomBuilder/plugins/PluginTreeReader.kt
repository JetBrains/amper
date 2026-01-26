/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.aomBuilder.plugins

import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.amper.frontend.AmperModule
import org.jetbrains.amper.frontend.FrontendPathResolver
import org.jetbrains.amper.frontend.SchemaBundle
import org.jetbrains.amper.frontend.TaskName
import org.jetbrains.amper.frontend.aomBuilder.ModuleBuildCtx
import org.jetbrains.amper.frontend.aomBuilder.createSchemaNode
import org.jetbrains.amper.frontend.api.DefaultTrace
import org.jetbrains.amper.frontend.api.isDefault
import org.jetbrains.amper.frontend.asBuildProblemSource
import org.jetbrains.amper.frontend.catalogs.substituteCatalogDependencies
import org.jetbrains.amper.frontend.contexts.EmptyContexts
import org.jetbrains.amper.frontend.messages.PsiBuildProblemSource
import org.jetbrains.amper.frontend.plugins.ModuleDataForPlugin
import org.jetbrains.amper.frontend.plugins.PluginYamlRoot
import org.jetbrains.amper.frontend.plugins.Task
import org.jetbrains.amper.frontend.plugins.generated.ShadowClasspath
import org.jetbrains.amper.frontend.plugins.generated.ShadowCompilationArtifact
import org.jetbrains.amper.frontend.plugins.generated.ShadowDependencyLocal
import org.jetbrains.amper.frontend.plugins.generated.ShadowModuleSources
import org.jetbrains.amper.frontend.plugins.generated.ShadowResolutionScope
import org.jetbrains.amper.frontend.plugins.generated.ShadowSourcesKind
import org.jetbrains.amper.frontend.project.AmperProjectContext
import org.jetbrains.amper.frontend.project.getTaskOutputRoot
import org.jetbrains.amper.frontend.reportBundleError
import org.jetbrains.amper.frontend.tree.BooleanNode
import org.jetbrains.amper.frontend.tree.ErrorNode
import org.jetbrains.amper.frontend.tree.MappingNode
import org.jetbrains.amper.frontend.tree.RefinedMappingNode
import org.jetbrains.amper.frontend.tree.TreeRefiner
import org.jetbrains.amper.frontend.tree.get
import org.jetbrains.amper.frontend.tree.mergeTrees
import org.jetbrains.amper.frontend.tree.reading.ReferencesParsingMode
import org.jetbrains.amper.frontend.tree.reading.readTree
import org.jetbrains.amper.frontend.tree.syntheticBuilder
import org.jetbrains.amper.frontend.types.PluginYamlTypingContext
import org.jetbrains.amper.frontend.types.SchemaTypingContext
import org.jetbrains.amper.frontend.types.getDeclaration
import org.jetbrains.amper.plugins.schema.model.PluginData
import org.jetbrains.amper.problems.reporting.FileBuildProblemSource
import org.jetbrains.amper.problems.reporting.Level
import org.jetbrains.amper.problems.reporting.MultipleLocationsBuildProblemSource
import org.jetbrains.amper.problems.reporting.ProblemReporter

internal class PluginTreeReader(
    private val projectContext: AmperProjectContext,
    val pluginModule: AmperModule,
    val pluginData: PluginData,
    pluginFile: VirtualFile,
    globalTypes: SchemaTypingContext,
    problemReporter: ProblemReporter,
    pathResolver: FrontendPathResolver,
) {
    private val treeRefiner = TreeRefiner()

    private val pluginTypes = PluginYamlTypingContext(globalTypes, pluginData)

    private val pluginTree: RefinedMappingNode = context(pluginTypes, problemReporter, pathResolver) {
        val tree = readTree(
            file = pluginFile,
            declaration = pluginTypes.getDeclaration<PluginYamlRoot>(),
            reportUnknowns = true,
            referenceParsingMode = ReferencesParsingMode.Parse,
            parseContexts = false,
        )

        // We do not resolve references here for the general plugin tree;
        // the reference-only values are added later for each module.
        treeRefiner.refineTree(tree, EmptyContexts, resolveReferences = false)
    }

    init {
        val tasks = pluginTree[PluginYamlRoot::tasks] as? MappingNode
        if (tasks == null || tasks.children.isEmpty()) {
            problemReporter.reportBundleError(
                source = tasks?.asBuildProblemSource() as? PsiBuildProblemSource
                    // If tasks are `{}` by *default*, then we need to use the whole tree trace.
                    ?: pluginTree.asBuildProblemSource(),
                messageKey = "plugin.missing.tasks",
                level = Level.Warning,
            )
        }
    }

    context(problemReporter: ProblemReporter)
    fun asAppliedTo(
        module: ModuleBuildCtx,
    ): PluginYamlRoot? = context(problemReporter, pluginTypes) {
        val moduleRootDir = module.module.source.moduleDir
        val pluginConfiguration = module.pluginsTree[pluginData.id.value] as? RefinedMappingNode
            ?: return@context null

        val enabled = (pluginConfiguration["enabled"] as? BooleanNode)?.value
        if (enabled != true) {
            reportExplicitValuesWhenDisabled(pluginConfiguration)
            return null
        }

        val taskDirs = (pluginTree[PluginYamlRoot::tasks] as? RefinedMappingNode)
            ?.refinedChildren
            ?.filterValues { it.value !is ErrorNode }
            ?.mapValues { (name, _) ->
                projectContext.getTaskOutputRoot(taskNameFor(module.module, name))
            }.orEmpty()

        // Build a tree with computed "reference-only" values.
        val referenceValuesTree = syntheticBuilder(DefaultTrace) {
            `object`<PluginYamlRoot> {
                if (pluginData.pluginSettingsSchemaName != null) {
                    PluginYamlRoot.PLUGIN_SETTINGS setTo pluginConfiguration
                }
                PluginYamlRoot::module setTo `object`<ModuleDataForPlugin> {
                    ModuleDataForPlugin::name setTo scalar(module.module.userReadableName)
                    ModuleDataForPlugin::rootDir setTo scalar(moduleRootDir)
                    val selfDependency = `object`<ShadowDependencyLocal> {
                        ShadowDependencyLocal::modulePath setTo scalar(moduleRootDir)
                    }
                    ModuleDataForPlugin::self setTo selfDependency
                    ModuleDataForPlugin::runtimeClasspath setTo `object`<ShadowClasspath> {
                        ShadowClasspath::dependencies setToList { add(selfDependency) }
                    }
                    ModuleDataForPlugin::compileClasspath setTo `object`<ShadowClasspath> {
                        ShadowClasspath::dependencies setToList { add(selfDependency) }
                        ShadowClasspath::scope setTo scalar(ShadowResolutionScope.Compile)
                    }
                    ModuleDataForPlugin::kotlinJavaSources setTo `object`<ShadowModuleSources> {
                        ShadowModuleSources::from setTo selfDependency
                    }
                    ModuleDataForPlugin::resources setTo `object`<ShadowModuleSources> {
                        ShadowModuleSources::from setTo selfDependency
                        ShadowModuleSources::kind setTo scalar(ShadowSourcesKind.Resources)
                    }
                    ModuleDataForPlugin::jar setTo `object`<ShadowCompilationArtifact> {
                        ShadowCompilationArtifact::from setTo selfDependency
                    }
                }
                PluginYamlRoot::tasks setToMap {
                    for ((taskName, taskBuildRoot) in taskDirs) {
                        taskName setTo `object`<Task> {
                            Task::taskOutputDir setTo scalar(taskBuildRoot)
                        }
                    }
                }
            }
        }

        val mergedTree = mergeTrees(pluginTree, referenceValuesTree)
            .substituteCatalogDependencies(pluginModule.usedCatalog)
        val refinedTree = treeRefiner.refineTree(mergedTree, EmptyContexts)
        createSchemaNode<PluginYamlRoot>(refinedTree)
    }

    fun taskNameFor(module: AmperModule, name: String) =
        TaskName.moduleTask(module, "$name@${pluginData.id.value}")

    context(problemReporter: ProblemReporter)
    private fun reportExplicitValuesWhenDisabled(pluginConfiguration: RefinedMappingNode) {
        val explicitValues = pluginConfiguration.children
            .filterNot { it.trace.isDefault }
        if (explicitValues.isNotEmpty()) {
            val source = MultipleLocationsBuildProblemSource(
                explicitValues.mapNotNull { it.asBuildProblemSource() as? FileBuildProblemSource },
                groupingMessage = SchemaBundle.message("plugin.unexpected.configuration.when.disabled.grouping"),
            )
            problemReporter.reportBundleError(
                source, "plugin.unexpected.configuration.when.disabled", pluginData.id.value,
                level = Level.Warning,
            )
        }
    }
}