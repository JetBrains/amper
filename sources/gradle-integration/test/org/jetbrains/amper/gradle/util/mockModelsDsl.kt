/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.gradle.util

import org.jetbrains.amper.frontend.Artifact
import org.jetbrains.amper.frontend.ClassBasedSet
import org.jetbrains.amper.frontend.CustomTaskDescription
import org.jetbrains.amper.frontend.Fragment
import org.jetbrains.amper.frontend.FragmentDependencyType
import org.jetbrains.amper.frontend.FragmentLink
import org.jetbrains.amper.frontend.Layout
import org.jetbrains.amper.frontend.LeafFragment
import org.jetbrains.amper.frontend.MetaModulePart
import org.jetbrains.amper.frontend.Model
import org.jetbrains.amper.frontend.ModulePart
import org.jetbrains.amper.frontend.Notation
import org.jetbrains.amper.frontend.Platform
import org.jetbrains.amper.frontend.AmperModule
import org.jetbrains.amper.frontend.LocalModuleDependency
import org.jetbrains.amper.frontend.AmperModuleFileSource
import org.jetbrains.amper.frontend.api.Trace
import org.jetbrains.amper.frontend.classBasedSet
import org.jetbrains.amper.frontend.schema.Module
import org.jetbrains.amper.frontend.schema.ProductType
import org.jetbrains.amper.frontend.schema.Settings
import java.nio.file.Path
import kotlin.io.path.Path

class MockModel(
    val name: String,
    override val projectRoot: Path,
) : Model {
    override val modules = mutableListOf<AmperModule>()

    fun module(buildFile: Path, builder: MockAmperModule.() -> Unit) =
        MockAmperModule(buildFile).apply(builder).apply { modules.add(this) }
}

class MockAmperModule(
    buildFile: Path,
    override var type: ProductType = ProductType.JVM_APP,
    override var userReadableName: String = "module",
    override val parts: ClassBasedSet<ModulePart<*>> = classBasedSet(),
) : AmperModule {
    override val source = AmperModuleFileSource(buildFile)
    override val fragments = mutableListOf<MockFragment>()
    override val artifacts = mutableListOf<MockArtifact>()
    override val usedCatalog = null
    override val customTasks: List<CustomTaskDescription> = emptyList()

    init {
        parts.add(MetaModulePart(layout = Layout.AMPER))
    }

    fun fragment(name: String = "common", builder: MockFragment.() -> Unit = {}) =
        MockFragment(name, this).apply(builder).apply { fragments.add(this) }

    fun leafFragment(
        name: String = "common",
        builder: LeafMockFragment.() -> Unit = {}
    ) = LeafMockFragment(name, this).apply(builder).apply { fragments.add(this) }

    fun artifact(
        name: String,
        platforms: Set<Platform>,
        vararg fragments: LeafMockFragment,
        builder: MockArtifact.() -> Unit = {}
    ): MockArtifact {
        check(platforms.all { it.isLeaf }) { "Cannot create an artifact with non leaf platform!" }
        return MockArtifact(name, platforms, fragments.asList(), isTest = false)
            .apply(builder)
            .apply { artifacts.add(this) }
    }
}

class MockFragmentLink(
    override val target: Fragment,
    override val type: FragmentDependencyType
) : FragmentLink

class MockLocalModuleDependency(override val module: AmperModule) : LocalModuleDependency {
    override var trace: Trace? = null
}

open class MockFragment(
    final override var name: String = "fragment",
    override val module: AmperModule,
    override val settings: Settings = Settings(),
) : Fragment {
    override val modifier: String = name
    override val fragmentDependencies = mutableListOf<FragmentLink>()
    override val externalDependencies = mutableListOf<Notation>()
    override val platforms = mutableSetOf<Platform>()
    override val isTest: Boolean = false
    override val isDefault: Boolean = true
    override val fragmentDependants = mutableListOf<FragmentLink>()
    override val src = Path(name, "src")
    override val resourcesPath = Path(name, "resources")
    override val composeResourcesPath = Path(name, "composeResources")
    override val hasAnyComposeResources: Boolean get() = false
    override val generatedSrcRelativeDirs = mutableListOf<Path>()
    override val generatedResourcesRelativeDirs = mutableListOf<Path>()
    override val generatedClassesRelativeDirs = mutableListOf<Path>()
    override val variants: List<String> = listOf()

    fun refines(other: MockFragment) = fragmentDependencies.add(
        MockFragmentLink(other, FragmentDependencyType.REFINE)
    )

    fun friends(other: MockFragment) = fragmentDependencies.add(
        MockFragmentLink(other, FragmentDependencyType.FRIEND)
    )

    fun dependency(notation: Notation) = externalDependencies.add(notation)
    fun dependency(module: MockAmperModule) = externalDependencies.add(MockLocalModuleDependency(module))
}

class LeafMockFragment(
    name: String = "fragment",
    module: AmperModule,
) : MockFragment(name, module), LeafFragment {
    override val platform get() = platforms.single()
}

class MockArtifact(
    override val name: String,
    override val platforms: Set<Platform>,
    override val fragments: List<LeafMockFragment>,
    override val isTest: Boolean,
) : Artifact
