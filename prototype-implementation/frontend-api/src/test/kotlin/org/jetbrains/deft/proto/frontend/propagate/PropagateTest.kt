package org.jetbrains.deft.proto.frontend.propagate

import org.jetbrains.deft.proto.frontend.*
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals

class PropagateTest {

    @Test
    fun `basic fragment property propagation`() {
        // given
        val model = generateSampleModel()

        // when
        val resultModel = model.propagatedFragments

        val part = resultModel
            .modules
            .first()
            .fragments
            .find { it.name == "jvm" }
            ?.parts
            ?.find { it.clazz == KotlinFragmentPart::class.java }
            ?.let { it.value as KotlinFragmentPart }

        assertEquals("1.9", part?.languageVersion)
    }

    private fun generateSampleModel() = object : Model {
        override val modules: List<PotatoModule>
            get() = buildList {
                add(object : PotatoModule {
                    override val userReadableName: String
                        get() = "test"
                    override val type: PotatoModuleType
                        get() = PotatoModuleType.APPLICATION
                    override val source: PotatoModuleSource
                        get() = PotatoModuleProgrammaticSource
                    override val fragments: List<Fragment>
                        get() = buildList {
                            val jvm = object : Fragment {
                                override val name: String
                                    get() = "jvm"
                                override val fragmentDependencies: List<FragmentLink>
                                    get() = buildList {
                                        add(FragmentLinkProvider {
                                            name = "common"
                                        })
                                    }
                                override val fragmentDependants: List<FragmentLink>
                                    get() = listOf()
                                override val externalDependencies: List<Notation>
                                    get() = listOf()
                                override val parts: ClassBasedSet<FragmentPart<*>>
                                    get() = buildSet {
                                        add(
                                            ByClassWrapper(
                                                KotlinFragmentPart(
                                                    "1.9",
                                                    null,
                                                    null,
                                                    listOf(),
                                                    listOf()
                                                )
                                            )
                                        )
                                    }
                                override val platforms: Set<Platform>
                                    get() = setOf(Platform.JVM)
                                override val src: Path?
                                    get() = null

                            }
                            add(jvm)
                            add(object : Fragment {
                                override val name: String
                                    get() = "common"
                                override val fragmentDependencies: List<FragmentLink>
                                    get() = listOf()
                                override val fragmentDependants: List<FragmentLink>
                                    get() = buildList {
                                        add(FragmentLinkProvider {
                                            name = "jvm"
                                        })
                                    }
                                override val externalDependencies: List<Notation>
                                    get() = listOf()
                                override val parts: ClassBasedSet<FragmentPart<*>>
                                    get() = buildSet {
                                        add(
                                            ByClassWrapper(
                                                KotlinFragmentPart(
                                                    "1.9",
                                                    null,
                                                    null,
                                                    listOf(),
                                                    listOf()
                                                )
                                            )
                                        )
                                    }
                                override val platforms: Set<Platform>
                                    get() = setOf(Platform.JVM)
                                override val src: Path?
                                    get() = null

                            })
                        }
                    override val artifacts: List<Artifact>
                        get() = listOf()

                })
            }
    }
}


context (PotatoModule) class FragmentLinkProvider(
    override val type: FragmentDependencyType,
    private val fragmentName: String
) : FragmentLink {
    override val target: Fragment
        get() = fragments
            .firstOrNull { it.name == fragmentName } ?: error("There is no such fragment with name: $fragmentName")

    class FragmentLinkProviderBuilder {
        var name: String = ""
        var type: FragmentDependencyType = FragmentDependencyType.REFINE
    }

    companion object {
        context (PotatoModule) operator fun invoke(init: FragmentLinkProviderBuilder.() -> Unit): FragmentLinkProvider {
            val builder = FragmentLinkProviderBuilder()
            builder.init()
            return FragmentLinkProvider(builder.type, builder.name)
        }
    }
}