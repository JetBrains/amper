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
import org.jetbrains.amper.frontend.api.isDefault
import org.jetbrains.amper.frontend.asBuildProblemSource
import org.jetbrains.amper.frontend.catalogs.substituteCatalogDependencies
import org.jetbrains.amper.frontend.contexts.EmptyContexts
import org.jetbrains.amper.frontend.messages.PsiBuildProblemSource
import org.jetbrains.amper.frontend.plugins.PluginYamlRoot
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
import org.jetbrains.amper.frontend.tree.add
import org.jetbrains.amper.frontend.tree.buildTree
import org.jetbrains.amper.frontend.tree.completeTree
import org.jetbrains.amper.frontend.tree.get
import org.jetbrains.amper.frontend.tree.instance
import org.jetbrains.amper.frontend.tree.invoke
import org.jetbrains.amper.frontend.tree.mergeTrees
import org.jetbrains.amper.frontend.tree.put
import org.jetbrains.amper.frontend.tree.reading.ReferencesParsingMode
import org.jetbrains.amper.frontend.tree.reading.readTree
import org.jetbrains.amper.frontend.types.SchemaTypingContext
import org.jetbrains.amper.frontend.types.generated.*
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
    types: SchemaTypingContext,
    problemReporter: ProblemReporter,
    pathResolver: FrontendPathResolver,
) {
    private val treeRefiner = TreeRefiner()

    private val pluginYamlDeclaration = types.pluginYamlDeclaration(pluginData.id)

    private val pluginTree: RefinedMappingNode = context(problemReporter, pathResolver) {
        val tree = readTree(
            file = pluginFile,
            declaration = pluginYamlDeclaration,
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
    ): PluginYamlRoot? = context(problemReporter) {
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
        val selfDependency = buildTree(DeclarationOfShadowDependencyLocal) {
            modulePath(moduleRootDir)
        }
        val referenceValuesTree = buildTree(pluginYamlDeclaration) {
            if (pluginData.pluginSettingsSchemaName != null) {
                pluginSettings(pluginConfiguration)
            }
            module {
                name(module.module.userReadableName)
                rootDir(moduleRootDir)
                self(selfDependency)
                runtimeClasspath {
                    dependencies { add(selfDependency) }
                }
                compileClasspath {
                    dependencies { add(selfDependency) }
                    scope(ShadowResolutionScope.Compile)
                }
                kotlinJavaSources { from(selfDependency) }
                resources {
                    from(selfDependency)
                    kind(ShadowSourcesKind.Resources)
                }
                jar { from(selfDependency) }

                // TODO: This will not include non-common non-main configuration.
                settings(module.moduleCtxModule.settings.backingTree)
                // TODO: Maybe at include test-settings here also?
            }
            tasks {
                for ((taskName, taskBuildRoot) in taskDirs) {
                    put[taskName] {
                        taskOutputDir(taskBuildRoot)
                    }
                }
            }
        }

        val mergedTree = mergeTrees(pluginTree, referenceValuesTree)
            .substituteCatalogDependencies(pluginModule.usedCatalog)
        treeRefiner.refineTree(mergedTree, EmptyContexts)
            .completeTree()?.instance<PluginYamlRoot>()
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