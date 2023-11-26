/*
 * Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.gradle.util

import org.jetbrains.amper.core.Result
import org.jetbrains.amper.core.messages.ProblemReporterContext
import org.jetbrains.amper.frontend.*
import java.nio.file.Path
import kotlin.io.path.Path

class MockModel(
    val name: String
) : Model {
    override val modules = mutableListOf<PotatoModule>()
    fun module(buildFile: Path, builder: MockPotatoModule.() -> Unit) =
        MockPotatoModule(buildFile).apply(builder).apply { modules.add(this) }
}

class MockPotatoModule(
        buildFile: Path,
        override var type: ProductType = ProductType.JVM_APP,
        override var userReadableName: String = "module",
        override val parts: ClassBasedSet<ModulePart<*>> = classBasedSet(),
) : PotatoModule {
    override val source = PotatoModuleFileSource(buildFile)
    override val fragments = mutableListOf<MockFragment>()
    override val artifacts = mutableListOf<MockArtifact>()
    init {
        parts.add(MetaModulePart(layout = Layout.AMPER))
    }

    fun fragment(name: String = "common", builder: MockFragment.() -> Unit = {}) =
        MockFragment(name).apply(builder).apply { fragments.add(this) }

    fun leafFragment(
        name: String = "common",
        builder: LeafMockFragment.() -> Unit = {}
    ) = LeafMockFragment(name).apply(builder).apply { fragments.add(this) }

    fun artifact(
        name: String,
        platforms: Set<Platform>,
        vararg fragments: LeafMockFragment,
        builder: MockArtifact.() -> Unit = {}
    ): MockArtifact {
        check(platforms.all { it.isLeaf }) { "Cannot create an artifact with non leaf platform!" }
        return MockArtifact(name, platforms, fragments.asList())
            .apply(builder)
            .apply { artifacts.add(this) }
    }
}

class MockFragmentLink(
    override val target: Fragment,
    override val type: FragmentDependencyType
) : FragmentLink

class MockPotatoDependency(private val myModule: PotatoModule) : PotatoModuleDependency {
    context(ProblemReporterContext)
    override val Model.module: Result<PotatoModule>
        get() = Result.success(myModule)
}

open class MockFragment(
    override var name: String = "fragment",
) : Fragment {
    override val fragmentDependencies = mutableListOf<FragmentLink>()
    override val externalDependencies = mutableListOf<Notation>()
    override val platforms = mutableSetOf<Platform>()
    override val isTest: Boolean = false
    override val isDefault: Boolean = true
    override val parts = classBasedSet<FragmentPart<*>>()
    override val fragmentDependants = mutableListOf<FragmentLink>()
    override val src = Path(name).resolve("src")
    override val resourcesPath = src.resolve("resources")
    override val variants: List<String> = listOf()

    fun refines(other: MockFragment) = fragmentDependencies.add(
        MockFragmentLink(other, FragmentDependencyType.REFINE)
    )

    fun friends(other: MockFragment) = fragmentDependencies.add(
        MockFragmentLink(other, FragmentDependencyType.FRIEND)
    )

    fun dependency(notation: Notation) = externalDependencies.add(notation)
    fun dependency(module: MockPotatoModule) = externalDependencies.add(MockPotatoDependency(module))
    fun addPart(part: FragmentPart<*>) = parts.add(part)
}

class LeafMockFragment(
    name: String = "fragment",
) : MockFragment(name), LeafFragment {
    override val platform get() = platforms.single()
}

class MockArtifact(
    override val name: String,
    override val platforms: Set<Platform>,
    override val fragments: List<LeafMockFragment>,
) : Artifact
