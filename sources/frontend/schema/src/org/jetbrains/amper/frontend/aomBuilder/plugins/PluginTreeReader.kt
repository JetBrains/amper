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
import org.jetbrains.amper.frontend.api.SchemaNode
import org.jetbrains.amper.frontend.api.isDefault
import org.jetbrains.amper.frontend.asBuildProblemSource
import org.jetbrains.amper.frontend.contexts.EmptyContexts
import org.jetbrains.amper.frontend.messages.PsiBuildProblemSource
import org.jetbrains.amper.frontend.plugins.ExtensionSchemaNode
import org.jetbrains.amper.frontend.plugins.PluginYamlRoot
import org.jetbrains.amper.frontend.plugins.Task
import org.jetbrains.amper.frontend.plugins.generated.ShadowClasspath
import org.jetbrains.amper.frontend.plugins.generated.ShadowDependencyLocal
import org.jetbrains.amper.frontend.project.AmperProjectContext
import org.jetbrains.amper.frontend.project.getTaskOutputRoot
import org.jetbrains.amper.frontend.reportBundleError
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
import org.jetbrains.amper.frontend.types.PluginYamlTypingContext
import org.jetbrains.amper.frontend.types.SchemaObjectDeclaration
import org.jetbrains.amper.frontend.types.SchemaObjectDeclarationBase
import org.jetbrains.amper.frontend.types.SchemaOrigin
import org.jetbrains.amper.frontend.types.SchemaType
import org.jetbrains.amper.frontend.types.getDeclaration
import org.jetbrains.amper.frontend.types.toType
import org.jetbrains.amper.plugins.schema.model.PluginData
import org.jetbrains.amper.problems.reporting.FileBuildProblemSource
import org.jetbrains.amper.problems.reporting.Level
import org.jetbrains.amper.problems.reporting.MultipleLocationsBuildProblemSource
import org.jetbrains.amper.problems.reporting.NonIdealDiagnostic
import org.jetbrains.amper.problems.reporting.WholeFileBuildProblemSource
import kotlin.collections.iterator

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
                    ?: @OptIn(NonIdealDiagnostic::class) WholeFileBuildProblemSource(pluginFile.toNioPath()),
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
            ?.mapValues { (name, _) ->
                projectContext.getTaskOutputRoot(taskNameFor(module.module, name))
            }.orEmpty()

        val configurationType = pluginConfiguration.type as SchemaType.ObjectType
        val classpathType = types.getDeclaration<ShadowClasspath>().toType()
        val moduleConfigurationDeclaration = object : SchemaObjectDeclarationBase() {
            override val properties = listOf(
                SchemaObjectDeclaration.Property(
                    "configuration", configurationType, origin = configurationType.declaration.origin, default = null,
                ),
                SchemaObjectDeclaration.Property(
                    "rootDir", SchemaType.PathType, origin = SchemaOrigin.Builtin, default = null,
                ),
                SchemaObjectDeclaration.Property(
                    "classpath", classpathType, origin = SchemaOrigin.Builtin, default = null,
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
                    "name" setTo scalar(module.module.userReadableName)
                    "configuration" setTo pluginConfiguration
                    "rootDir" setTo scalar(moduleRootDir)
                    "dependency" setTo `object`<ShadowDependencyLocal> {
                        ShadowDependencyLocal::modulePath setTo scalar(moduleRootDir)
                    }
                    "classpath" setTo `object`<ShadowClasspath> {
                        ShadowClasspath::dependencies setToList {
                            add(`object`<ShadowDependencyLocal> {
                                ShadowDependencyLocal::modulePath setTo scalar(moduleRootDir)
                            })
                        }
                    }.appendDefaultValues()
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