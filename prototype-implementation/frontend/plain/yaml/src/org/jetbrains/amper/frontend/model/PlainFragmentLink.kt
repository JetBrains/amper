package org.jetbrains.amper.frontend.model

import org.jetbrains.amper.frontend.*

context (Stateful<FragmentBuilder, Fragment>, TypesafeVariants)
internal class PlainFragmentLink(private val mutableFragmentDependency: MutableFragmentDependency) : FragmentLink {
    override val target: Fragment
        get() = mutableFragmentDependency.target.build()
    override val type: FragmentDependencyType
        get() = when (mutableFragmentDependency.dependencyKind) {
            MutableFragmentDependency.DependencyKind.Friend -> FragmentDependencyType.FRIEND
            MutableFragmentDependency.DependencyKind.Refines -> FragmentDependencyType.REFINE
        }
}