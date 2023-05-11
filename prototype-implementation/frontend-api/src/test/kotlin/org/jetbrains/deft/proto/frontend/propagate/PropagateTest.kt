package org.jetbrains.deft.proto.frontend.propagate

import org.jetbrains.deft.proto.frontend.*
import kotlin.test.Test
import kotlin.test.assertEquals

class PropagateTest {

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

        val model = object : Model {
            override val modules: List<PotatoModule>
                get() = listOf(module)

        }

        // when
        val resultModel = model.resolved

        val part =
            resultModel.modules.first().fragments.find { it.name == "jvm" }?.parts?.find { it.clazz == KotlinFragmentPart::class.java }
                ?.let { it.value as KotlinFragmentPart }

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

        val model = object : Model {
            override val modules: List<PotatoModule>
                get() = listOf(module)

        }

        // when
        val resultModel = model.resolved

        val part =
            resultModel.modules.first().fragments.find { it.name == "darwin" }?.parts?.find { it.clazz == KotlinFragmentPart::class.java }
                ?.let { it.value as KotlinFragmentPart }

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

        val model = object : Model {
            override val modules: List<PotatoModule>
                get() = listOf(module)

        }

        val resultModel = model.resolved


        assertEquals(
            "1.9",
            resultModel.modules.first().fragments.find { it.name == "jvm" }?.parts?.find { it.clazz == KotlinFragmentPart::class.java }
                ?.let { it.value as KotlinFragmentPart }?.apiVersion
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

        val model = object : Model {
            override val modules: List<PotatoModule>
                get() = listOf(module)

        }

        val resultModel = model.resolved

        assertEquals(
            "MainKt",
            resultModel.modules.first().artifacts.find { it.name == "jvm" }?.parts?.find { it.clazz == JavaApplicationArtifactPart::class.java }
                ?.let { it.value as JavaApplicationArtifactPart }?.mainClass
        )
    }
}
