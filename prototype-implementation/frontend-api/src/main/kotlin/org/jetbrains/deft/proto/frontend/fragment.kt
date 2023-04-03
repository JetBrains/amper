package org.jetbrains.deft.proto.frontend

sealed interface Notation
interface PotatoModuleDependency : Notation {
    // A dirty hack to make module resolution lazy.
    val Model.module: PotatoModule
}
data class MavenDependency(val coordinates: String) : Notation

enum class FragmentDependencyType {
    REFINE,
    FRIEND,
}

/**
 * Dependency between fragments.
 * Can differ by type (refines dependency, test on sources dependency, etc.).
 */
interface FragmentDependency {
    val target: Fragment
    val type: FragmentDependencyType
}

sealed interface FragmentPart<SelfT>

data class KotlinFragmentPart(
    val languageVersion: String?,
    val apiVersion: String?,
    val progressiveMode: Boolean?,
    val languageFeatures: List<String>,
    val optIns: List<String>,
    val srcFolderName: String? = null,
) : FragmentPart<KotlinFragmentPart>

/**
 * Some part of the module that supports "single resolve context" invariant for
 * every source and resource file that is included.
 */
interface Fragment {
    val name: String
    val fragmentDependencies: List<FragmentDependency>
    val externalDependencies: List<Notation>
    val parts: ClassBasedSet<FragmentPart<*>>
}
