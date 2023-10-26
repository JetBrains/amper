package org.jetbrains.amper.frontend.propagate

import org.jetbrains.amper.frontend.*
import org.jetbrains.amper.frontend.resolve.resolved
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

        val model = ModelImpl(module)

        // when
        val resultModel = model.resolved

        val part = resultModel.modules.first().fragments.find { it.name == "jvm" }
            ?.parts
            ?.findInstance<KotlinPart>()

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

        val model = ModelImpl(module)

        // when
        val resultModel = model.resolved

        val part = resultModel.modules.first().fragments.find { it.name == "darwin" }
            ?.parts
            ?.findInstance<KotlinPart>()

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

        val model = ModelImpl(module)
        val resultModel = model.resolved

        assertEquals(
            "1.9",
            resultModel.modules.first().fragments.find { it.name == "jvm" }
                ?.parts?.findInstance<KotlinPart>()?.apiVersion
        )
    }

    @Test
    fun `artifact receives default values`() {
        val module = potatoModule("main") {
            fragment("common") {
                dependant("jvm")
            }
            fragment("jvm") {
                dependsOn("common")
                jvmPart {}
            }
        }

        val model = ModelImpl(module)
        val resultModel = model.resolved

        val jvmFragment = resultModel.modules.first().fragments.find { it.name == "jvm" }
        val parts = jvmFragment?.parts
        assertEquals(
            "17",
            parts?.find<JvmPart>()?.target
        )
    }

    @Test
    fun `android namespace propagation`() {
        val module = potatoModule("androidApp") {
            fragment("common") {
                dependant("android")
                androidPart {
                    namespace = "namespace"
                }
            }
            fragment("android") {
                dependsOn("common")
                androidPart {}
            }
        }

        val model = ModelImpl(module)
        val resultModel = model.resolved

        val actualNamespace = resultModel
            .modules
            .first()
            .fragments
            .find { it.name == "android" }
            ?.parts
            ?.find<AndroidPart>()
            ?.namespace

        assertEquals("namespace", actualNamespace)
    }
}
