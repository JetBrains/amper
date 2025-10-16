/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.aomBuilder

import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.amper.frontend.AmperModule
import org.jetbrains.amper.frontend.AmperModuleFileSource
import org.jetbrains.amper.frontend.Artifact
import org.jetbrains.amper.frontend.ClassBasedSet
import org.jetbrains.amper.frontend.DefaultScopedNotation
import org.jetbrains.amper.frontend.Fragment
import org.jetbrains.amper.frontend.Layout
import org.jetbrains.amper.frontend.LeafFragment
import org.jetbrains.amper.frontend.LocalModuleDependency
import org.jetbrains.amper.frontend.Model
import org.jetbrains.amper.frontend.ModulePart
import org.jetbrains.amper.frontend.Platform
import org.jetbrains.amper.frontend.VersionCatalog
import org.jetbrains.amper.frontend.api.Trace
import org.jetbrains.amper.frontend.classBasedSet
import org.jetbrains.amper.frontend.plugins.MavenPluginXml
import org.jetbrains.amper.frontend.plugins.TaskFromPluginDescription
import org.jetbrains.amper.frontend.schema.ProductType
import java.nio.file.Path
import kotlin.io.path.pathString

data class DefaultModel(
    override val projectRoot: Path,
    override val modules: List<AmperModule>,
    override val unreadableModuleFiles: Set<VirtualFile>,
) : Model

internal open class DefaultModule(
    override val userReadableName: String,
    override val type: ProductType,
    override val source: AmperModuleFileSource,
    override val aliases: Map<String, Set<Platform>>,
    override val usedCatalog: VersionCatalog?,
    override val usedTemplates: List<VirtualFile>,
    override var parts: ClassBasedSet<ModulePart<*>> = classBasedSet(),
    override val layout: Layout = Layout.AMPER,
) : AmperModule {
    override var fragments = emptyList<Fragment>()
    override var artifacts = emptyList<Artifact>()
    override var tasksFromPlugins = emptyList<TaskFromPluginDescription>()
    override var mavenPluginXmls = emptyList<MavenPluginXml>()
}

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
    override val trace: Trace,
    override val compile: Boolean = true,
    override val runtime: Boolean = true,
    override val exported: Boolean = false,
) : LocalModuleDependency, DefaultScopedNotation {
    override fun toString() = "InternalDependency(module=${path.pathString})"
}