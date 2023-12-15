/*
 * Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend

import org.jetbrains.amper.frontend.api.TraceableString
import org.jetbrains.amper.frontend.schema.*
import org.jetbrains.amper.frontend.schema.ProductType
import org.jetbrains.amper.frontend.helper.convertTest
import org.jetbrains.amper.frontend.old.helper.TestWithBuildFile
import kotlin.test.Ignore
import kotlin.test.Test

class ConverterTest : TestWithBuildFile() {

    // TODO Check that there are all of settings withing that file.
    @Test
    @Ignore
    fun `all module settings are converted without errors`() =
        moduleConvertTest("all-module-settings", expectedModule = testModuleAllSettings())

    @Test
    fun `all module settings are converted without errors - psi`() =
        moduleConvertPsiTest("all-module-settings", expectedModule = testModuleAllSettings())

    private fun testModuleAllSettings(): Module {
        return Module().apply {
            product(
                ModuleProduct().apply {
                    type(ProductType.LIB)
                    platforms(listOf(Platform.JVM, Platform.ANDROID, Platform.IOS_ARM64))
                }
            )
    //                apply(emptyList())
            aliases(mapOf("jvmAndAndroid" to setOf(Platform.JVM, Platform.ANDROID)))

    //                repositories(emptyList())
            dependencies(
                mapOf(
                    setOf(TraceableString("jvmAndAndroid")) to listOf(
                        ExternalMavenDependency().apply {
                            coordinates("org.jetbrains.compose.runtime:runtime:1.4.1")
                        }),
                    setOf(TraceableString("jvm")) to listOf(
                        ExternalMavenDependency().apply {
                            coordinates("io.ktor:ktor-server-core:2.3.2")
                        },
                        ExternalMavenDependency().apply {
                            coordinates("org.jetbrains.compose.material3:material3:1.4.1")
                        },
                        ExternalMavenDependency().apply {
                            coordinates("some.dep")
                            `compile-only`(true)
                        },
                        ExternalMavenDependency().apply {
                            coordinates("other.dep")
                            `compile-only`(true)
                            exported(true)
                        }
                    ),
                    setOf(TraceableString("android")) to listOf(
                        ExternalMavenDependency().apply {
                            coordinates("androidx.compose.animation:animation-graphics:1.4.3")
                        }),
                )
            )

            `test-dependencies`(
                mapOf(
                    setOf(TraceableString("")) to listOf(
                        ExternalMavenDependency().apply {
                            exported(true)
                            coordinates("androidx.activity:activity-compose:1.6.1")
                        },
                        ExternalMavenDependency().apply {
                            `compile-only`(true)      // todo (AB) : what is a proper default?
                            coordinates("androidx.activity:activity-compose:1.6.2")
                        },
                        ExternalMavenDependency().apply {
                            `runtime-only`(true)
                            coordinates("androidx.activity:activity-compose:1.6.3")
                        },
                    )
                )
            )

            settings(
                mapOf(
                    setOf(TraceableString("")) to Settings().apply {
                        android(
                            AndroidSettings().apply {
                                compileSdk("33")
                                minSdk("30")
                                applicationId("my-application")
                                maxSdk("33")
                                targetSdk("33")
                                namespace("com.example.namespace")
                            }
                        )
                        compose(
                            ComposeSettings().apply {
                                enabled(true)
                            }
                        )
                        jvm(
                            JvmSettings().apply {
                                mainClass("MainKt")
                            }
                        )
                        kotlin(
                            KotlinSettings().apply {
                                apiVersion("1.8")
                                languageVersion("1.8")
                                allWarningsAsErrors(false)
                                debug(false)
                                progressiveMode(false)
                                suppressWarnings(false)
                                verbose(true)
                                freeCompilerArgs(listOf("-Xinline-classes", "-Xxxx"))
                                optIns(listOf("kotlinx.Experimental"))
                            }
                        )
                    }
                )
            )
            `test-settings`(
                mapOf(
                    setOf(TraceableString("")) to Settings().apply {
                        compose(
                            ComposeSettings().apply {
                                enabled(true)
                            }
                        )
                    }
                )
            )
        }
    }

    @Test
    @Ignore
    fun `all template settings are converted without errors`() {
        TODO()
    }

    @Test
    @Ignore
    fun `all module settings are converted correctly`() {
        TODO()
    }

    @Test
    @Ignore
    fun `all template settings are converted correctly`() {
        TODO()
    }

    @Test
    @Ignore
    fun `redundant module settings are causing errors`() {
        TODO()
    }

    @Test
    @Ignore
    fun `redundant template settings are causing errors`() {
        TODO()
    }

}