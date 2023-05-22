package org.jetbrains.deft.proto.frontend.model

import org.jetbrains.deft.proto.frontend.*
import java.nio.file.Path

context (Stateful<FragmentBuilder, Fragment>)
internal class PlainFragment(
        private val fragmentBuilder: FragmentBuilder
) : Fragment {
    override val name: String
        get() = fragmentBuilder.name
    override val fragmentDependencies: List<FragmentLink>
        get() = fragmentBuilder.dependencies.map { PlainFragmentLink(it) }
    override val fragmentDependants: List<FragmentLink>
        get() = fragmentBuilder.dependants.map { PlainFragmentLink(it) }
    override val externalDependencies: List<Notation>
        get() = fragmentBuilder.externalDependencies.toList()
    override val parts: ClassBasedSet<FragmentPart<*>>
        get() = buildClassBasedSet {
            fragmentBuilder.kotlin?.let {
                add(KotlinFragmentPart(
                        it.languageVersion?.toString(),
                        it.apiVersion?.toString(),
                        it.progressiveMode,
                        it.languageFeatures,
                        it.optIns,
                ))
            }

            fragmentBuilder.junit?.let {
                add(TestFragmentPart(it.platformEnabled))
            }
        }
    override val platforms: Set<Platform>
        get() = fragmentBuilder.platforms
    override val src: Path?
        get() = fragmentBuilder.src
}