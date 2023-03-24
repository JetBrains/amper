package org.jetbrains.deft.proto.frontend

/**
 * Just a renaming for a possible future class introduction.
 */
typealias Notation = String

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
