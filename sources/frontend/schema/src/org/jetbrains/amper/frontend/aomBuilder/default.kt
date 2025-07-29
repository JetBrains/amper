/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.aomBuilder

import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.amper.frontend.AddToModuleRootsFromCustomTask
import org.jetbrains.amper.frontend.AmperModule
import org.jetbrains.amper.frontend.AmperModuleInvalidPathSource
import org.jetbrains.amper.frontend.AmperModuleSource
import org.jetbrains.amper.frontend.Artifact
import org.jetbrains.amper.frontend.ClassBasedSet
import org.jetbrains.amper.frontend.CompositeString
import org.jetbrains.amper.frontend.CustomTaskDescription
import org.jetbrains.amper.frontend.DefaultScopedNotation
import org.jetbrains.amper.frontend.Fragment
import org.jetbrains.amper.frontend.LeafFragment
import org.jetbrains.amper.frontend.LocalModuleDependency
import org.jetbrains.amper.frontend.Model
import org.jetbrains.amper.frontend.ModulePart
import org.jetbrains.amper.frontend.Platform
import org.jetbrains.amper.frontend.PublishArtifactFromCustomTask
import org.jetbrains.amper.frontend.TaskName
import org.jetbrains.amper.frontend.VersionCatalog
import org.jetbrains.amper.frontend.api.Trace
import org.jetbrains.amper.frontend.classBasedSet
import org.jetbrains.amper.frontend.customTaskSchema.CustomTaskNode
import org.jetbrains.amper.frontend.customTaskSchema.CustomTaskType
import org.jetbrains.amper.frontend.plugins.TaskFromPluginDescription
import org.jetbrains.amper.frontend.schema.ProductType
import java.nio.file.Path
import kotlin.io.path.pathString

data class DefaultModel(
    override val projectRoot: Path,
    override val modules: List<AmperModule>,
) : Model

internal open class DefaultModule(
    override val userReadableName: String,
    override val type: ProductType,
    override val source: AmperModuleSource,
    override val usedCatalog: VersionCatalog?,
    override val usedTemplates: List<VirtualFile>,
    override var parts: ClassBasedSet<ModulePart<*>> = classBasedSet(),
) : AmperModule {
    override var fragments = emptyList<Fragment>()
    override var artifacts = emptyList<Artifact>()
    override var customTasks = emptyList<CustomTaskDescription>()
    override var tasksFromPlugins = emptyList<TaskFromPluginDescription>()
}

class DefaultPublishArtifactFromCustomTask(
    override val pathWildcard: String,
    override val artifactId: String,
    override val classifier: String,
    override val extension: String,
): PublishArtifactFromCustomTask

class DefaultAddToModuleRootsFromCustomTask(
    override val taskOutputRelativePath: Path,
    override val type: AddToModuleRootsFromCustomTask.Type,
    override val isTest: Boolean,
    override val platform: Platform,
): AddToModuleRootsFromCustomTask

class DefaultCustomTaskDescription(
    override val name: TaskName,
    override val source: Path,
    override val origin: CustomTaskNode,
    override val type: CustomTaskType,
    override val module: AmperModule,
    override val jvmArguments: List<CompositeString>,
    override val programArguments: List<CompositeString>,
    override val environmentVariables: Map<String, CompositeString>,
    override val dependsOn: List<TaskName>,
    override val publishArtifacts: List<PublishArtifactFromCustomTask>,
    override val customTaskCodeModule: AmperModule,
    override val addToModuleRootsFromCustomTask: List<AddToModuleRootsFromCustomTask>,
) : CustomTaskDescription

class DefaultTaskFromPluginDescription(
    override val name: TaskName,
    override val actionClassJvmName: String,
    override val actionFunctionJvmName: String,
    override val actionArguments: Map<String, Any?>,
    override val explicitDependsOn: List<String>,
    override val inputs: List<Path>,
    override val outputs: Map<Path, TaskFromPluginDescription.OutputMark?>,
    override val codeSource: AmperModule,
) : TaskFromPluginDescription

/**
 * Special kind of module that appears only on
 * internal module resolve failure.
 */
internal class NotResolvedModule(
    userReadableName: String,
    invalidPath: Path,
) : DefaultModule(
    userReadableName = userReadableName,
    type = ProductType.LIB,
    source = AmperModuleInvalidPathSource(invalidPath),
    usedCatalog = null,
    usedTemplates = emptyList(),
    parts = classBasedSet(),
)

class DefaultArtifact(
    override val name: String,
    override val fragments: List<LeafFragment>,
    override val isTest: Boolean,
) : Artifact {
    override val platforms = fragments.flatMap { it.platforms }.toSet()
}

// TODO Should it be data class?
// The only concern here is how [module] is compared.
// But, since [DefaultModule] seems to have no [equals] overwrite, 
// thus is will be compared by reference, and that is fine.
data class DefaultLocalModuleDependency(
    override val module: AmperModule,
    val path: Path,
    override val trace: Trace?,
    override val compile: Boolean = true,
    override val runtime: Boolean = true,
    override val exported: Boolean = false,
) : LocalModuleDependency, DefaultScopedNotation {
    override fun toString() = "InternalDependency(module=${path.pathString})"
}