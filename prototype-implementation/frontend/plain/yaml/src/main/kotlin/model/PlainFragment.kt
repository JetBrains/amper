package org.jetbrains.deft.proto.frontend.model

import org.jetbrains.deft.proto.frontend.*
import org.jetbrains.deft.proto.frontend.MutableFragmentDependency
import java.nio.file.Path

context (Stateful<FragmentBuilder, Fragment>)
internal class PlainFragment(
    private val fragmentBuilder: FragmentBuilder
) : Fragment {
    override val name: String
        get() = fragmentBuilder.name
    override val fragmentDependencies: List<FragmentLink>
        get() = fragmentBuilder.dependencies.map {
            object : FragmentLink {
                override val target: Fragment
                    get() = it.target.build()
                override val type: FragmentDependencyType
                    get() = when (it.dependencyKind) {
                        MutableFragmentDependency.DependencyKind.Friend -> FragmentDependencyType.FRIEND
                        MutableFragmentDependency.DependencyKind.Refines -> FragmentDependencyType.REFINE
                    }
            }
        }
    override val fragmentDependants: List<FragmentLink>
        get() = TODO("Not yet implemented")
    override val externalDependencies: List<Notation>
        get() = fragmentBuilder.externalDependencies.toList()
    override val parts: ClassBasedSet<FragmentPart<*>>
        get() = buildSet {
            fragmentBuilder.kotlin?.let {
                add(
                    ByClassWrapper(
                        KotlinFragmentPart(
                            it.languageVersion.toString(),
                            it.apiVersion.toString(),
                            it.progressiveMode,
                            it.languageFeatures,
                            it.optIns,
                        )
                    )
                )
            }

            fragmentBuilder.junit?.let {
                add(ByClassWrapper(TestFragmentPart(it.platformEnabled)))
            }
        }
    override val src: Path?
        get() = fragmentBuilder.src
}