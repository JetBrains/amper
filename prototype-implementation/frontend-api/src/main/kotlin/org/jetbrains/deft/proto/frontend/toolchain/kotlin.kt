package org.jetbrains.deft.proto.frontend.toolchain

import org.jetbrains.deft.proto.frontend.Fragment
import org.jetbrains.deft.proto.frontend.FragmentDependency
import java.nio.file.Path

interface KotlinFragment : Fragment {
    val sources: List<Path>
    val platforms: List<String>
}

interface InternalKotlinFragmentDependency : FragmentDependency {
    override val type: String
        get() = "internal"
}

interface RefineKotlinFragmentDependency : FragmentDependency {
    override val type: String
        get() = "refine"
}