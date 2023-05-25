package org.jetbrains.deft.proto.frontend.propagate

import org.jetbrains.deft.proto.frontend.*
import kotlin.test.Test
import kotlin.test.assertEquals

class PropagateTest {

    abstract class EmptyPartsModel : Model {
        override val parts: ClassBasedSet<ModelPart<*>> = classBasedSet()
    }

    @Test
    fun `basic fragment property propagation`() {
        // given
        val module = potatoModule("main") {
            fragment("common") {
                dependant("jvm")
                kotlinPart {
                    languageVersion = "1.9"
                }
            }
            fragment("jvm") {
                dependsOn("common")
            }
        }

        val model = object : EmptyPartsModel() {
            override val modules: List<PotatoModule>
                get() = listOf(module)

        }

        // when
        val resultModel = model.resolved

        val part = resultModel.modules.first().fragments.find { it.name == "jvm" }
                    ?.parts
                    ?.findInstance<KotlinFragmentPart>()

        assertEquals("1.9", part?.languageVersion)
    }

    @Test
    fun `multi level propagation`() {
        val module = potatoModule("main") {
            fragment("common") {
                dependant("native")
                kotlinPart {
                    languageVersion = "1.9"
                }
            }
            fragment("native") {
                dependant("darwin")
                dependsOn("common")
            }

            fragment("darwin") {
                dependsOn("native")
            }
        }

        val model = object : EmptyPartsModel() {
            override val modules: List<PotatoModule>
                get() = listOf(module)

        }

        // when
        val resultModel = model.resolved

        val part = resultModel.modules.first().fragments.find { it.name == "darwin" }
                    ?.parts
                    ?.findInstance<KotlinFragmentPart>()

        assertEquals("1.9", part?.languageVersion)
    }

    @Test
    fun `set default values`() {
        val module = potatoModule("main") {
            fragment("common") {
                dependant("jvm")
                kotlinPart {
                    languageVersion = "1.9"
                }
            }
            fragment("jvm") {
                dependsOn("common")
            }
        }

        val model = object : EmptyPartsModel() {
            override val modules: List<PotatoModule>
                get() = listOf(module)

        }

        val resultModel = model.resolved


        assertEquals(
            "1.9",
            resultModel.modules.first().fragments.find { it.name == "jvm" }
                    ?.parts?.findInstance<KotlinFragmentPart>()?.apiVersion
        )
    }

    @Test
    fun `artifact receives default values`() {
        val module = potatoModule("main") {
            fragment("common") {
                dependant("jvm")
            }
            fragment("jvm") {
                val fragment = this
                dependsOn("common")
                artifact {
                    name = "jvm"
                    javaPart {
                        packagePrefix = "org.jetbrains.deft"
                        fragment(fragment)
                    }
                }
            }
        }

        val model = object : EmptyPartsModel() {
            override val modules: List<PotatoModule>
                get() = listOf(module)

        }

        val resultModel = model.resolved

        assertEquals(
            "MainKt",
            resultModel.modules.first().artifacts.find { it.name == "jvm" }
                    ?.parts?.findInstance<JavaArtifactPart>()?.mainClass
        )
    }
}
