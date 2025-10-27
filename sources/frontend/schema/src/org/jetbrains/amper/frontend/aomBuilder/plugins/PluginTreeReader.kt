/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.aomBuilder.plugins

import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.amper.frontend.AmperModule
import org.jetbrains.amper.frontend.SchemaBundle
import org.jetbrains.amper.frontend.TaskName
import org.jetbrains.amper.frontend.aomBuilder.BuildCtx
import org.jetbrains.amper.frontend.aomBuilder.ModuleBuildCtx
import org.jetbrains.amper.frontend.aomBuilder.createSchemaNode
import org.jetbrains.amper.frontend.api.DefaultTrace
import org.jetbrains.amper.frontend.api.isDefault
import org.jetbrains.amper.frontend.asBuildProblemSource
import org.jetbrains.amper.frontend.catalogs.substituteCatalogDependencies
import org.jetbrains.amper.frontend.contexts.EmptyContexts
import org.jetbrains.amper.frontend.messages.PsiBuildProblemSource
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
import org.jetbrains.amper.frontend.tree.ErrorValue
import org.jetbrains.amper.frontend.tree.MapLikeValue
import org.jetbrains.amper.frontend.tree.Refined
import org.jetbrains.amper.frontend.tree.TreeRefiner
import org.jetbrains.amper.frontend.tree.TreeValue
import org.jetbrains.amper.frontend.tree.appendDefaultValues
import org.jetbrains.amper.frontend.tree.asMapLike
import org.jetbrains.amper.frontend.tree.get
import org.jetbrains.amper.frontend.tree.reading.ReferencesParsingMode
import org.jetbrains.amper.frontend.tree.reading.readTree
import org.jetbrains.amper.frontend.tree.resolveReferences
import org.jetbrains.amper.frontend.tree.scalarValue
import org.jetbrains.amper.frontend.tree.single
import org.jetbrains.amper.frontend.tree.syntheticBuilder
import org.jetbrains.amper.frontend.types.ModuleDataForPluginDeclaration
import org.jetbrains.amper.frontend.types.PluginYamlTypingContext
import org.jetbrains.amper.frontend.types.SchemaObjectDeclaration
import org.jetbrains.amper.frontend.types.getDeclaration
import org.jetbrains.amper.plugins.schema.model.PluginData
import org.jetbrains.amper.problems.reporting.FileBuildProblemSource
import org.jetbrains.amper.problems.reporting.Level
import org.jetbrains.amper.problems.reporting.MultipleLocationsBuildProblemSource

internal class PluginTreeReader(
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
            referenceParsingMode = ReferencesParsingMode.Parse,
            parseContexts = false,
        )

        val withDefaults = treeMerger.mergeTrees(tree)
            .appendDefaultValues()
        treeRefiner.refineTree(withDefaults, EmptyContexts) as MapLikeValue<Refined>
    }

    init {
        val tasks = pluginTree["tasks"].singleOrNull()?.value as? MapLikeValue<*>
        if (tasks == null || tasks.children.isEmpty()) {
            buildCtx.problemReporter.reportBundleError(
                source = tasks?.asBuildProblemSource() as? PsiBuildProblemSource
                    // If tasks are `{}` by *default*, then we need to use the whole tree trace.
                    ?: pluginTree.asBuildProblemSource(),
                messageKey = "plugin.missing.tasks",
                level = Level.Warning,
            )
        }
    }

    fun asAppliedTo(
        module: ModuleBuildCtx,
    ): PluginYamlRoot? = with(this@PluginTreeReader.buildCtx) {
        val moduleRootDir = module.module.source.moduleDir
        val pluginConfiguration = module.commonTree
            .asMapLikeAndGet("plugins")
            ?.asMapLikeAndGet(pluginData.id.value)
            ?.asMapLike

        val enabled = pluginConfiguration?.get("enabled")?.singleOrNull()?.value?.scalarValue<Boolean>()
        if (enabled != true) {
            if (pluginConfiguration != null) {
                reportExplicitValuesWhenDisabled(pluginConfiguration)
            }
            return null
        }

        val taskDirs = (pluginTree.asMapLikeAndGet("tasks") as? Refined)
            ?.refinedChildren
            ?.filterValues { it.value !is ErrorValue }
            ?.mapValues { (name, _) ->
                projectContext.getTaskOutputRoot(taskNameFor(module.module, name))
            }.orEmpty()

        val moduleConfigurationDeclaration =
            types.getDeclaration(ModuleDataForPluginDeclaration) as SchemaObjectDeclaration

        // Build a tree with computed "reference-only" values.
        val referenceValuesTree = syntheticBuilder(this@PluginTreeReader.buildCtx.types, DefaultTrace) {
            `object`<PluginYamlRoot> {
                if (pluginData.pluginSettingsSchemaName != null) {
                    PluginYamlRoot.PLUGIN_SETTINGS setTo pluginConfiguration
                }
                PluginYamlRoot.MODULE setTo `object`(moduleConfigurationDeclaration.toType()) {
                    ModuleDataForPluginDeclaration.NAME setTo scalar(module.module.userReadableName)
                    ModuleDataForPluginDeclaration.ROOT_DIR setTo scalar(moduleRootDir)
                    val selfDependency = `object`<ShadowDependencyLocal> {
                        ShadowDependencyLocal::modulePath setTo scalar(moduleRootDir)
                    }
                    ModuleDataForPluginDeclaration.SELF setTo selfDependency
                    ModuleDataForPluginDeclaration.RUNTIME_CLASSPATH setTo `object`<ShadowClasspath> {
                        ShadowClasspath::dependencies setToList { add(selfDependency) }
                    }.appendDefaultValues()
                    ModuleDataForPluginDeclaration.COMPILE_CLASSPATH setTo `object`<ShadowClasspath> {
                        ShadowClasspath::dependencies setToList { add(selfDependency) }
                        ShadowClasspath::scope setTo scalar(ShadowResolutionScope.Compile)
                    }.appendDefaultValues()
                    ModuleDataForPluginDeclaration.KOTLIN_JAVA_SOURCES setTo `object`<ShadowModuleSources> {
                        ShadowModuleSources::from setTo selfDependency
                    }.appendDefaultValues()
                    ModuleDataForPluginDeclaration.RESOURCES setTo `object`<ShadowModuleSources> {
                        ShadowModuleSources::from setTo selfDependency
                        ShadowModuleSources::kind setTo scalar(ShadowSourcesKind.Resources)
                    }.appendDefaultValues()
                    ModuleDataForPluginDeclaration.JAR setTo `object`<ShadowCompilationArtifact> {
                        ShadowCompilationArtifact::from setTo selfDependency
                    }.appendDefaultValues()
                }
                PluginYamlRoot::tasks setToMap {
                    for ((taskName, taskBuildRoot) in taskDirs) {
                        taskName setTo `object`<Task> {
                            Task.TASK_OUTPUT_DIR setTo scalar(taskBuildRoot)
                        }
                    }
                }
            }
        }

        val mergedTree = treeMerger.mergeTrees(listOfNotNull(pluginTree, referenceValuesTree))
            .substituteCatalogDependencies(pluginModule.usedCatalog)
        val refinedTree = treeRefiner.refineTree(mergedTree, EmptyContexts)
        val resolvedTree = refinedTree.resolveReferences()
        createSchemaNode<PluginYamlRoot>(resolvedTree)
    }

    fun taskNameFor(module: AmperModule, name: String) =
        TaskName.moduleTask(module, "$name@${pluginData.id.value}")

    context(context: BuildCtx)
    private fun reportExplicitValuesWhenDisabled(pluginConfiguration: MapLikeValue<Refined>) {
        val explicitValues = pluginConfiguration.children
            .map { it.value }.filterNot { it.trace.isDefault }
        if (explicitValues.isNotEmpty()) {
            val source = MultipleLocationsBuildProblemSource(
                explicitValues.mapNotNull { it.asBuildProblemSource() as? FileBuildProblemSource },
                groupingMessage = SchemaBundle.message("plugin.unexpected.configuration.when.disabled.grouping"),
            )
            context.problemReporter.reportBundleError(
                source, "plugin.unexpected.configuration.when.disabled", pluginData.id.value,
                level = Level.Warning,
            )
        }
    }

    private fun TreeValue<Refined>.asMapLikeAndGet(property: String): TreeValue<Refined>? {
        return (this as? Refined)?.single(property)?.value
    }
}