package org.jetbrains.deft.proto.gradle

import org.jetbrains.deft.proto.frontend.*

/**
 * A class, that caches [modules] and also, adds
 * some other functionality.
 */
data class ModelWrapper(
    val model: Model,
) : Model {
    override val modules = ArrayList(model.modules)
}

class PotatoModuleWrapper(
    private val passedModule: PotatoModule
) : PotatoModule by passedModule {
    // Wrapper functions.
    private val allFragmentWrappers = mutableMapOf<Fragment, FragmentWrapper>()
    val LeafFragment.wrappedLeaf get() = wrapped as LeafFragmentWrapper
    val Fragment.wrapped
        get() = allFragmentWrappers.computeIfAbsent(this) {
            when (this) {
                is FragmentWrapper -> this
                is LeafFragment -> LeafFragmentWrapper(this)
                else -> FragmentWrapper(this)
            }
        }

    val artifactPlatforms by lazy { artifacts.flatMap { it.platforms }.toSet() }
    val fragmentsByName by lazy { fragments.associateBy { it.name } }
    override val artifacts = passedModule.artifacts.map { it.wrap(this) }
    override val fragments = passedModule.fragments.map { it.wrapped }
    val leafFragments = passedModule.fragments.filterIsInstance<LeafFragment>().map { it.wrappedLeaf }
    val leafNonTestFragments = leafFragments
        .filter { !it.isTest }
    val leafTestFragments = leafFragments
        .filter { it.isTest }
}

fun Artifact.wrap(module: PotatoModuleWrapper) =
    if (this is TestArtifact) TestArtifactWrapper(this, module)
    else ArtifactWrapper(this, module)

interface PlatformAware {
    val platforms: Set<Platform>
}

open class ArtifactWrapper(
    artifact: Artifact,
    private val module: PotatoModuleWrapper,
) : Artifact by artifact, PlatformAware {
    override val fragments = artifact.fragments.map { with(module) { it.wrappedLeaf } }
}

class TestArtifactWrapper(
    artifact: TestArtifact,
    module: PotatoModuleWrapper
) : ArtifactWrapper(artifact, module), TestArtifact

open class FragmentWrapper(
    private val fragment: Fragment
) : Fragment by fragment, PlatformAware {
    override fun toString(): String = "FragmentWrapper(fragment=${fragment.name})"
}

@Suppress("DELEGATED_MEMBER_HIDES_SUPERTYPE_OVERRIDE")
class LeafFragmentWrapper(
    fragment: LeafFragment
) : FragmentWrapper(fragment), LeafFragment by fragment, PlatformAware