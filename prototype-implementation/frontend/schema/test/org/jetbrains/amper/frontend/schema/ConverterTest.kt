/*
 * Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.schema

import org.jetbrains.amper.frontend.Platform
import org.jetbrains.amper.frontend.api.TraceableString
import org.jetbrains.amper.frontend.old.helper.TestBase
import org.jetbrains.amper.frontend.schema.*
import org.jetbrains.amper.frontend.schema.helper.convertTest
import org.jetbrains.amper.frontend.schema.ProductType
import kotlin.io.path.Path
import kotlin.io.path.div
import kotlin.test.Test

class ConverterTest : TestBase(Path("testResources") / "converter") {

    // TODO Check that there are all of settings withing that file.
    @Test
    fun `all module settings are converted without errors`() =
        convertTest("all-module-settings", expectedModule = testModuleAllSettings())

    @Test
    fun `all module settings are converted without errors - psi`() =
        convertTest("all-module-settings", usePsiConverter = true, expectedModule = testModuleAllSettings())

    private fun testModuleAllSettings(): Module {
        return Module().apply {
            product(
                ModuleProduct().apply {
                    type(ProductType.LIB)
                    platforms(listOf(Platform.JVM, Platform.ANDROID, Platform.IOS_ARM64))
                }
            )
            module(
                Meta().apply {
                    layout(AmperLayout.GRADLE)
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
                            scope(DependencyScope.COMPILE_ONLY)
                        },
                        ExternalMavenDependency().apply {
                            coordinates("other.dep")
                            scope(DependencyScope.COMPILE_ONLY)
                            exported(true)
                        },
                        CatalogDependency().apply {
                            catalogKey("kotlin-stdlib")
                            scope(DependencyScope.RUNTIME_ONLY)
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
                    emptySet<TraceableString>() to listOf(
                        ExternalMavenDependency().apply {
                            exported(true)
                            coordinates("androidx.activity:activity-compose:1.6.1")
                        },
                        ExternalMavenDependency().apply {
                            scope(DependencyScope.COMPILE_ONLY)
                            coordinates("androidx.activity:activity-compose:1.6.2")
                        },
                        ExternalMavenDependency().apply {
                            scope(DependencyScope.RUNTIME_ONLY)
                            coordinates("androidx.activity:activity-compose:1.6.3")
                        },
                        CatalogDependency().apply {
                            catalogKey("kotlin-test")
                        }
                    )
                )
            )

            settings(
                mapOf(
                    emptySet<TraceableString>() to Settings().apply {
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
                        ios(
                            IosSettings().apply {
                                teamId("iosTeam")
                                framework(
                                    IosFrameworkSettings().apply {
                                        basename("iosBasename")
                                        isStatic(true)
                                        mappings(mapOf("someArbitraryKeyName" to "xxx"))
                                    }
                                )
                            }
                        )
                        publishing(
                            PublishingSettings().apply {
                                group("org.jetbrains.amper.frontend.without-fragments.yaml")
                                version("42-SNAPSHOT")
                            }
                        )
                        kover(
                            KoverSettings().apply {
                                enabled(true)
                                html(
                                    KoverHtmlSettings().apply {
                                        title("koverHtmlReportTitle")
                                        onCheck(true)
                                        reportDir("html")
                                        charset("UTF8")
                                    }
                                )
                                xml(
                                    KoverXmlSettings().apply {
                                        onCheck(false)
                                        reportFile("coverage.xml")
                                    }
                                )
                            }
                        )
                    }
                )
            )
            `test-settings`(
                mapOf(
                    emptySet<TraceableString>() to Settings().apply {
                        compose(
                            ComposeSettings().apply {
                                enabled(true)
                            }
                        )
                    }
                )
            )
            repositories(
                listOf(
                    Repository().apply {
                        id("scratch")
                        url("https://packages.jetbrains.team/maven/p/amper/amper")
                        publish(true)
                        credentials(Repository.Credentials().apply {
                            usernameKey("tName")
                            passwordKey("tPass")
                            file(java.nio.file.Path.of("testResources", "converter", "local.properties").toAbsolutePath())
                        })
                    },
                    Repository().apply {
                        id("https://packages.jetbrains.team/maven/p/deft/deft")
                        url("https://packages.jetbrains.team/maven/p/deft/deft")
                    },
                )
            )
        }
    }

    fun `all template settings are converted without errors`() {
        TODO()
    }

    fun `all module settings are converted correctly`() {
        TODO()
    }

    fun `all template settings are converted correctly`() {
        TODO()
    }

    fun `redundant module settings are causing errors`() {
        TODO()
    }

    fun `redundant template settings are causing errors`() {
        TODO()
    }

}