/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.aomBuilder

import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.amper.core.messages.ProblemReporterContext
import org.jetbrains.amper.frontend.AddToModuleRootsFromCustomTask
import org.jetbrains.amper.frontend.Artifact
import org.jetbrains.amper.frontend.CompositeString
import org.jetbrains.amper.frontend.CustomTaskDescription
import org.jetbrains.amper.frontend.Fragment
import org.jetbrains.amper.frontend.LeafFragment
import org.jetbrains.amper.frontend.Model
import org.jetbrains.amper.frontend.ModulePart
import org.jetbrains.amper.frontend.Platform
import org.jetbrains.amper.frontend.AmperModule
import org.jetbrains.amper.frontend.AmperModuleFileSource
import org.jetbrains.amper.frontend.AmperModuleInvalidPathSource
import org.jetbrains.amper.frontend.AmperModuleSource
import org.jetbrains.amper.frontend.PublishArtifactFromCustomTask
import org.jetbrains.amper.frontend.TaskName
import org.jetbrains.amper.frontend.VersionCatalog
import org.jetbrains.amper.frontend.classBasedSet
import org.jetbrains.amper.frontend.customTaskSchema.CustomTaskNode
import org.jetbrains.amper.frontend.customTaskSchema.CustomTaskType
import org.jetbrains.amper.frontend.schema.Module
import org.jetbrains.amper.frontend.schema.ProductType
import java.nio.file.Path

data class DefaultModel(
    override val projectRoot: Path,
    override val modules: List<AmperModule>,
) : Model

context(ProblemReporterContext)
internal open class DefaultModule(
    override val userReadableName: String,
    override val type: ProductType,
    override val source: AmperModuleSource,
    final override val origin: Module,
    override val usedCatalog: VersionCatalog?,
) : AmperModule {
    override var fragments = emptyList<Fragment>()
    override var artifacts = emptyList<Artifact>()
    override var customTasks = emptyList<CustomTaskDescription>()
    override var parts = origin.convertModuleParts()
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

context(ProblemReporterContext)
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

/**
 * Special kind of module that appears only on
 * internal module resolve failure.
 */
context(ProblemReporterContext)
internal class NotResolvedModule(
    userReadableName: String,
    invalidPath: Path,
) : DefaultModule(
    userReadableName = userReadableName,
    type = ProductType.LIB,
    source = AmperModuleInvalidPathSource(invalidPath),
    origin = Module(),
    usedCatalog = null,
)

class DefaultArtifact(
    override val name: String,
    override val fragments: List<LeafFragment>,
    override val isTest: Boolean,
) : Artifact {
    override val platforms = fragments.flatMap { it.platforms }.toSet()
}

class DumbGradleModule(val gradleBuildFile: VirtualFile) : AmperModule {
    override val userReadableName = gradleBuildFile.parent.name
    override val type = ProductType.LIB
    override val source = AmperModuleFileSource(gradleBuildFile.toNioPath())
    override val origin = Module()
    override val fragments = listOf<Fragment>()
    override val artifacts = listOf<Artifact>()
    override val parts = classBasedSet<ModulePart<*>>()
    override val usedCatalog = null
    override val customTasks: List<CustomTaskDescription> = emptyList()
}
