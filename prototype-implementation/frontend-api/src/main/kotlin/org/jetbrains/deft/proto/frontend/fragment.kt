package org.jetbrains.deft.proto.frontend

import java.nio.file.Path

sealed interface Notation
interface PotatoModuleDependency : Notation {
    // A dirty hack to make module resolution lazy.
    val Model.module: PotatoModule
}

data class MavenDependency(val coordinates: String) : Notation

enum class FragmentDependencyType {
    REFINE, FRIEND,
}

/**
 * Dependency between fragments.
 * Can differ by type (refines dependency, test on sources dependency, etc.).
 */
interface FragmentLink {
    val target: Fragment
    val type: FragmentDependencyType
}

sealed interface FragmentPart<SelfT> {
    fun propagate(parent: SelfT): FragmentPart<*>
}

data class KotlinFragmentPart(
    val languageVersion: String?,
    val apiVersion: String?,
    val progressiveMode: Boolean?,
    val languageFeatures: List<String>,
    val optIns: List<String>,
) : FragmentPart<KotlinFragmentPart> {
    override fun propagate(parent: KotlinFragmentPart): FragmentPart<KotlinFragmentPart> = KotlinFragmentPart(
        parent.languageVersion ?: languageVersion,
        parent.apiVersion ?: apiVersion,
        parent.progressiveMode ?: progressiveMode,
        parent.languageFeatures + languageFeatures,
        parent.optIns + optIns,
    )
}

data class TestFragmentPart(val junitPlatform: Boolean?) : FragmentPart<TestFragmentPart> {
    override fun propagate(parent: TestFragmentPart): FragmentPart<*> =
        TestFragmentPart(parent.junitPlatform ?: junitPlatform)
}

/**
 * Some part of the module that supports "single resolve context" invariant for
 * every source and resource file that is included.
 */
interface Fragment {
    val name: String
    val fragmentDependencies: List<FragmentLink>
    val fragmentDependants: List<FragmentLink>
    val externalDependencies: List<Notation>
    val parts: ClassBasedSet<FragmentPart<*>>
    val platforms: Set<Platform>
    val src: Path?
}
