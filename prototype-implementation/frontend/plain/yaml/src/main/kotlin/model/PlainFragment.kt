package org.jetbrains.deft.proto.frontend.model

import org.jetbrains.deft.proto.frontend.*
import org.jetbrains.deft.proto.frontend.MutableFragmentDependency

context (Stateful<MutableFragment, Fragment>)
internal class PlainFragment(private val mutableFragment: MutableFragment) : Fragment {
    override val name: String
        get() = mutableFragment.name
    override val fragmentDependencies: List<FragmentDependency>
        get() = mutableFragment.dependencies.map {
            object : FragmentDependency {
                override val target: Fragment
                    get() = it.target.immutable()
                override val type: FragmentDependencyType
                    get() = when (it.dependencyKind) {
                        MutableFragmentDependency.DependencyKind.Friend -> FragmentDependencyType.FRIEND
                        MutableFragmentDependency.DependencyKind.Refines -> FragmentDependencyType.REFINE
                    }
            }
        }
    override val externalDependencies: List<Notation>
        get() = mutableFragment.externalDependencies.toList()
    override val parts: ClassBasedSet<FragmentPart<*>>
        get() = setOf(
            ByClassWrapper(
                KotlinFragmentPart(
                    mutableFragment.languageVersion.toString(),
                    mutableFragment.apiVersion.toString(),
                    mutableFragment.progressiveMode,
                    mutableFragment.languageFeatures,
                    mutableFragment.optIns,
                    mutableFragment.srcFolderName
                )
            )
        )
}