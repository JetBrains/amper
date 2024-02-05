/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.schema

import org.jetbrains.amper.frontend.Fragment
import org.jetbrains.amper.frontend.Model
import org.jetbrains.amper.frontend.PotatoModule
import org.jetbrains.amper.frontend.aomBuilder.ModelImpl
import org.jetbrains.amper.frontend.aomBuilder.resolved
import org.jetbrains.amper.frontend.schema.helper.potatoModule
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

class PropagateTest {

    private fun ModelImpl(vararg modules: PotatoModule) = ModelImpl(modules.toList())

    @Test
    fun `basic fragment property propagation`() {
        // given
        val module = potatoModule("main") {
            fragment("common") {
                dependant("jvm")
                kotlin {
                    languageVersion = KotlinVersion.Kotlin19
                }
            }
            fragment("jvm") {
                dependsOn("common")
            }
        }

        val model = ModelImpl(module)

        // when
        val resultModel = model.resolved

        val jvmFragment = assertSingleFragment(resultModel, "jvm")
        assertEquals(KotlinVersion.Kotlin19, jvmFragment.settings.kotlin.languageVersion)
    }

    @Test
    fun `multi level propagation`() {
        val module = potatoModule("main") {
            fragment("common") {
                dependant("native")
                kotlin {
                    languageVersion = KotlinVersion.Kotlin19
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

        val darwinFragment = assertSingleFragment(resultModel, "darwin")
        assertEquals(KotlinVersion.Kotlin19, darwinFragment.settings.kotlin.languageVersion)
    }

    @Test
    fun `set default values`() {
        val module = potatoModule("main") {
            fragment("common") {
                dependant("jvm")
                kotlin {
                    languageVersion = KotlinVersion.Kotlin19
                }
            }
            fragment("jvm") {
                dependsOn("common")
            }
        }

        val model = ModelImpl(module)
        val resultModel = model.resolved

        val jvmFragment = assertSingleFragment(resultModel, "jvm")
        assertEquals(KotlinVersion.Kotlin19, jvmFragment.settings.kotlin.apiVersion)
    }

    @Test
    fun `artifact receives default values`() {
        val module = potatoModule("main") {
            fragment("common") {
                dependant("jvm")
            }
            fragment("jvm") {
                dependsOn("common")
                jvm {}
            }
        }

        val model = ModelImpl(module)
        val resultModel = model.resolved

        val jvmFragment = assertSingleFragment(resultModel, "jvm")
        assertEquals(JavaVersion.VERSION_17, jvmFragment.settings.jvm.target)
    }

    @Test
    fun `android namespace propagation`() {
        val module = potatoModule("androidApp") {
            fragment("common") {
                dependant("android")
                android {
                    namespace = "namespace"
                }
            }
            fragment("android") {
                dependsOn("common")
            }
        }

        val model = ModelImpl(module)
        val resultModel = model.resolved

        val androidFragment = assertSingleFragment(resultModel, "android")
        assertEquals("namespace", androidFragment.settings.android.namespace)
    }

    @Test
    fun `android params propagation`() {
        val module = potatoModule("androidApp") {
            fragment("common") {
                dependant("android")
                android {
                    applicationId = "com.example.applicationid"
                    namespace = "com.example.namespace"
                    minSdk = AndroidVersion.VERSION_30
                    maxSdk = AndroidVersion.VERSION_33
                    compileSdk = AndroidVersion.VERSION_33
                    targetSdk = AndroidVersion.VERSION_33
                }
            }
            fragment("android") {
                dependsOn("common")
            }
        }

        val model = ModelImpl(module)
        val resultModel = model.resolved

        val androidFragment = assertSingleFragment(resultModel, "android")
        val androidSettings = androidFragment.settings.android

        assertEquals("com.example.applicationid", androidSettings.applicationId)
        assertEquals("com.example.namespace", androidSettings.namespace)
        assertEquals(AndroidVersion.VERSION_30, androidSettings.minSdk)
        assertEquals(AndroidVersion.VERSION_33, androidSettings.maxSdk)
        assertEquals(AndroidVersion.VERSION_33, androidSettings.compileSdk)
        assertEquals(AndroidVersion.VERSION_33, androidSettings.targetSdk)
    }

    @Test
    fun `diamond propagation`() {
        //     common
        //     /    \
        // desktop  apple
        //     \    /
        //    macosX64
        val module = potatoModule("androidApp") {
            fragment("common") {
                dependant("desktop")
                dependant("apple")
                kotlin {
                    optIns = listOf("kotlin.contracts.ExperimentalContracts")
                }
            }
            fragment("apple") {
                dependsOn("common")
                dependant("macosX64")
                kotlin {
                    debug = true
                    languageVersion = KotlinVersion.Kotlin18
                }
            }
            fragment("desktop") {
                dependsOn("common")
                dependant("macosX64")
                kotlin {
                    allWarningsAsErrors = true
                }
            }
            fragment("macosX64") {
                dependsOn("apple")
                dependsOn("desktop")
            }
        }

        val model = ModelImpl(module)
        val resultModel = model.resolved

        val macosX64Fragment = assertSingleFragment(resultModel, "macosX64")
        val kotlinSettings = macosX64Fragment.settings.kotlin

        assertEquals(listOf("kotlin.contracts.ExperimentalContracts"), kotlinSettings.optIns, "should inherit from 'common' ancestor")
        assertTrue(kotlinSettings.debug, "should inherit 'debug' from 'apple' parent")
        assertEquals(KotlinVersion.Kotlin18, kotlinSettings.languageVersion, "should inherit 'languageVersion' from 'apple' parent")
        assertTrue(kotlinSettings.allWarningsAsErrors, "should inherit from 'desktop' parent")
    }

    private fun assertSingleFragment(resultModel: Model, fragmentName: String): Fragment {
        val module = resultModel.modules.singleOrNull() ?: fail("Expected a single module")
        return module.fragments.singleOrNull { it.name == fragmentName }
            ?: fail("Expected a single fragment named '$fragmentName'")
    }
}
