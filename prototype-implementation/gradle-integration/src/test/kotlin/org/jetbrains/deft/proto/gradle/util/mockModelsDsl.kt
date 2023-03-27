package org.jetbrains.deft.proto.gradle.util

import org.jetbrains.deft.proto.frontend.*
import java.nio.file.Path

class MockModel(
    val name: String
) : Model {
    override val modules = mutableListOf<PotatoModule>()
    fun module(buildFile: Path, builder: MockPotatoModule.() -> Unit) =
        MockPotatoModule(buildFile).apply(builder).apply { modules.add(this) }
}

class MockPotatoModule(
    buildFile: Path,
    override var type: PotatoModuleType = PotatoModuleType.APPLICATION,
    override var userReadableName: String = "module",
) : PotatoModule {
    override val source = PotatoModuleFileSource(buildFile)
    override val fragments = mutableListOf<MockFragment>()
    override val artifacts = mutableListOf<MockArtifact>()
    fun fragment(name: String = "fragment", builder: MockFragment.() -> Unit = {}) =
        MockFragment(name).apply(builder).apply { fragments.add(this) }

    fun artifact(
        name: String,
        platforms: Set<Platform>,
        vararg fragments: MockFragment,
        builder: MockArtifact.() -> Unit = {}
    ): MockArtifact {
        check(platforms.all { it.isLeaf }) { "Cannot create an artifact with non leaf platform!" }
        return MockArtifact(name, platforms, fragments.asList())
            .apply(builder)
            .apply { artifacts.add(this) }
    }
}

class MockFragmentDependency(
    override val target: Fragment,
    override val type: FragmentDependencyType
) : FragmentDependency

class MockFragment(
    override var name: String = "fragment",
) : Fragment {
    override val fragmentDependencies = mutableListOf<FragmentDependency>()
    override val externalDependencies = mutableListOf<Notation>()
    override val parts = mutableSetOf<ByClassWrapper<FragmentPart<*>>>()
    fun refines(other: MockFragment) = fragmentDependencies.add(
        MockFragmentDependency(other, FragmentDependencyType.REFINE)
    )

    fun friends(other: MockFragment) = fragmentDependencies.add(
        MockFragmentDependency(other, FragmentDependencyType.FRIEND)
    )

    fun dependency(notation: Notation) = externalDependencies.add(notation)
    fun addPart(part: FragmentPart<*>) = parts.add(ByClassWrapper(part))
}

class MockArtifact(
    override val name: String,
    override val platforms: Set<Platform>,
    override val fragments: List<MockFragment>,
) : Artifact {
    override val parts = mutableSetOf<ByClassWrapper<ArtifactPart<*>>>()
    fun addPart(part: ArtifactPart<*>) = parts.add(ByClassWrapper(part))
}