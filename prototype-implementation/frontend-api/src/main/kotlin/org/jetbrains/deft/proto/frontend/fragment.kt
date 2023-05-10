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
    context (Fragment) fun propagate(): FragmentPart<SelfT>
}

data class KotlinFragmentPart(
    val languageVersion: String?,
    val apiVersion: String?,
    val progressiveMode: Boolean?,
    val languageFeatures: List<String>,
    val optIns: List<String>,
) : FragmentPart<KotlinFragmentPart> {
    context (Fragment) override fun propagate(): KotlinFragmentPart {

        var part: KotlinFragmentPart? = null

        for (fragmentDependency in fragmentDependencies) {
            val fragment = fragmentDependency.target

            fragment.parts.firstOrNull { it.clazz == KotlinFragmentPart::class.java }?.let {
                val propagatedPart = with(fragment) { (it.value as KotlinFragmentPart).propagate() }
                part = if (part == null) {
                    propagatedPart
                } else {
                    KotlinFragmentPart(
                        propagatedPart.languageVersion ?: part!!.languageVersion,
                        propagatedPart.apiVersion ?: part!!.apiVersion,
                        propagatedPart.progressiveMode ?: part!!.progressiveMode,
                        propagatedPart.languageFeatures + part!!.languageFeatures,
                        propagatedPart.optIns + part!!.optIns,
                    )
                }
            }
        }

        return part ?: this
    }
}

data class TestFragmentPart(val junitPlatform: Boolean?) : FragmentPart<TestFragmentPart> {
    context (Fragment) override fun propagate(): TestFragmentPart {
        var part: TestFragmentPart? = null

        for (fragmentDependency in fragmentDependencies) {
            val fragment = fragmentDependency.target

            fragment.parts.firstOrNull { it.clazz == TestFragmentPart::class.java }?.let {
                val propagatedPart = with(fragment) { (it.value as TestFragmentPart).propagate() }
                part = if (part == null) {
                    propagatedPart
                } else {
                    TestFragmentPart(propagatedPart.junitPlatform ?: part!!.junitPlatform)
                }
            }
        }

        return part!!
    }
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
