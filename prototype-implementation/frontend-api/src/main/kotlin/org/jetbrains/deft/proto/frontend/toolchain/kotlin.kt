package org.jetbrains.deft.proto.frontend.toolchain

import org.jetbrains.deft.proto.frontend.Fragment
import java.nio.file.Path

interface KotlinFragment : Fragment {
    val sources: List<Path>
    val platforms: List<String>
    val dependencies: List<String>
}