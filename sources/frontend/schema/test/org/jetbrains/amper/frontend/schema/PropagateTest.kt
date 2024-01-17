package org.jetbrains.amper.frontend.schema

import org.jetbrains.amper.frontend.AndroidPart
import org.jetbrains.amper.frontend.JvmPart
import org.jetbrains.amper.frontend.KotlinPart
import org.jetbrains.amper.frontend.ModelImpl
import org.jetbrains.amper.frontend.aomBuilder.resolved
import org.jetbrains.amper.frontend.findInstance
import org.jetbrains.amper.frontend.schema.helper.potatoModule
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

    @Test
    fun `android params propagation`() {
        val module = potatoModule("androidApp") {
            fragment("common") {
                dependant("android")
                androidPart {
                    applicationId = "com.example.applicationid"
                    namespace = "com.example.namespace"
                    minSdk = "30"
                    maxSdk = 33
                    compileSdk = "33"
                    targetSdk = "33"
                }
            }
            fragment("android") {
                dependsOn("common")
                androidPart {}
            }
        }

        val model = ModelImpl(module)
        val resultModel = model.resolved

        val androidPart = resultModel
            .modules
            .first()
            .fragments
            .find { it.name == "android" }
            ?.parts
            ?.find<AndroidPart>()!!

        assertEquals("com.example.applicationid", androidPart.applicationId)
        assertEquals("com.example.namespace", androidPart.namespace)
        assertEquals("30", androidPart.minSdk)
        assertEquals(33, androidPart.maxSdk)
        assertEquals("33", androidPart.compileSdk)
        assertEquals("33", androidPart.targetSdk)
    }
}