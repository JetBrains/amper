package org.jetbrains.deft.proto.frontend.propagate

import org.jetbrains.deft.proto.frontend.*
import java.nio.file.Path

class ResolvedModel(override val modules: List<PotatoModule>) : Model

class ResolvedPotatoModule(
    override val userReadableName: String,
    override val type: PotatoModuleType,
    override val source: PotatoModuleSource,
    override val fragments: List<Fragment>,
    override val artifacts: List<Artifact>
) : PotatoModule

class ResolvedFragment(
    override val name: String,
    override val fragmentDependencies: List<FragmentLink>,
    override val fragmentDependants: List<FragmentLink>,
    override val externalDependencies: List<Notation>,
    override val parts: ClassBasedSet<FragmentPart<*>>,
    override val src: Path?,
    override val platforms: Set<Platform>
) : Fragment