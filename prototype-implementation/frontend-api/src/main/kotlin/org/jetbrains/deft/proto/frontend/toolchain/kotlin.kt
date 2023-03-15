package org.jetbrains.deft.proto.frontend.toolchain

import org.jetbrains.deft.proto.frontend.Fragment
import org.jetbrains.deft.proto.frontend.FragmentDependency
import org.jetbrains.deft.proto.frontend.FragmentDependencyType
import java.nio.file.Path

interface KotlinFragment : Fragment {
    val sources: List<Path>
}

interface InternalKotlinFragmentDependency : FragmentDependency {
    override val type: FragmentDependencyType get() = FragmentDependencyType.REFINE
}

interface RefineKotlinFragmentDependency : FragmentDependency {
    override val type: FragmentDependencyType get() = FragmentDependencyType.FRIEND
}