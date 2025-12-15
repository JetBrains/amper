/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.maven

import org.jetbrains.amper.frontend.api.DefaultTrace
import org.jetbrains.amper.frontend.api.TraceableString
import org.jetbrains.amper.frontend.api.TransformedValueTrace
import org.jetbrains.amper.frontend.contexts.TestCtx
import org.jetbrains.amper.frontend.schema.DependencyScope
import org.jetbrains.amper.frontend.schema.ExternalMavenBomDependency
import org.jetbrains.amper.frontend.schema.ExternalMavenDependency
import org.jetbrains.amper.frontend.schema.JavaAnnotationProcessingSettings
import org.jetbrains.amper.frontend.schema.JavaSettings
import org.jetbrains.amper.frontend.schema.JvmSettings
import org.jetbrains.amper.frontend.schema.KotlinSettings
import org.jetbrains.amper.frontend.schema.Module
import org.jetbrains.amper.frontend.schema.ModuleProduct
import org.jetbrains.amper.frontend.schema.ProductType
import org.jetbrains.amper.frontend.schema.Project
import org.jetbrains.amper.frontend.schema.SerializationSettings
import org.jetbrains.amper.frontend.schema.Settings
import org.jetbrains.amper.frontend.schema.SpringBootSettings
import org.jetbrains.amper.frontend.schema.UnscopedExternalMavenDependency
import org.jetbrains.amper.frontend.tree.mergeTrees
import org.jetbrains.amper.frontend.tree.syntheticBuilder
import org.jetbrains.amper.frontend.types.SchemaType
import org.jetbrains.amper.frontend.types.SchemaTypingContext
import org.jetbrains.amper.frontend.types.maven.Configuration
import org.jetbrains.amper.frontend.types.maven.Dependencies
import org.jetbrains.amper.frontend.types.maven.MavenPluginXml
import org.jetbrains.amper.frontend.types.maven.Mojo
import org.jetbrains.amper.frontend.types.maven.Mojos
import org.jetbrains.amper.frontend.types.maven.Parameter
import org.jetbrains.amper.frontend.types.maven.Parameters
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class RenderToYamlTest {

    private val dummyTransformedTrace = TransformedValueTrace(
        "dummy",
        TraceableString("dummy", DefaultTrace),
    )

    @Test
    fun `product without platforms and basic settings`() {
        val tree = syntheticBuilder(SchemaTypingContext(emptyList(), emptyList()), DefaultTrace) {
            `object`<Module> {
                Module::product {
                    ModuleProduct::type setTo scalar(ProductType.JVM_APP)
                }

                Module::settings {
                    Settings::jvm {
                        JvmSettings::release setTo scalar("17")
                    }
                }
            }
        }

        val yaml = tree.serializeToYaml()
        assertEquals("""
            product: jvm/app
            
            settings:
              jvm:
                release: 17

        """.trimIndent(), yaml)
    }

    @Test
    fun `java annotation processors are rendered`() {
        val tree = syntheticBuilder(SchemaTypingContext(emptyList(), emptyList()), DefaultTrace) {
            `object`<Module> {
                Module::product {
                    ModuleProduct::type setTo scalar(ProductType.JVM_APP)
                }

                Module::settings {
                    Settings::java {
                        JavaSettings::annotationProcessing {
                            JavaAnnotationProcessingSettings::processors {
                                this += `object`<UnscopedExternalMavenDependency> {
                                    UnscopedExternalMavenDependency::coordinates setTo scalar("com.example:proc-one:1.0.0")
                                }
                                this += `object`<UnscopedExternalMavenDependency> {
                                    UnscopedExternalMavenDependency::coordinates setTo scalar("com.acme:proc-two:2.1.3")
                                }
                            }
                        }
                    }
                }
            }
        }

        val yaml = tree.serializeToYaml()
        assertEquals(
            """
            product: jvm/app
            
            settings:
              java:
                annotationProcessing:
                  processors:
                    - com.example:proc-one:1.0.0
                    - com.acme:proc-two:2.1.3

            """.trimIndent(),
            yaml
        )
    }

    @Test
    fun `spring boot shorthand example`() {
        val tree = syntheticBuilder(SchemaTypingContext(emptyList(), emptyList()), DefaultTrace) {
            `object`<Module> {
                Module::product {
                    ModuleProduct::type setTo scalar(ProductType.JVM_APP)
                }

                Module::settings {
                    Settings::springBoot {
                        SpringBootSettings::enabled setTo scalar(true)
                    }
                }
            }
        }

        val yaml = tree.serializeToYaml()
        assertEquals("""
            product: jvm/app
            
            settings:
              springBoot: enabled

        """.trimIndent(), yaml)
    }

    @Test
    fun `serialization shorthand example`() {
        val tree = syntheticBuilder(SchemaTypingContext(emptyList(), emptyList()), DefaultTrace) {
            `object`<Module> {
                Module::product {
                    ModuleProduct::type setTo scalar(ProductType.JVM_APP)
                }

                Module::settings {
                    Settings::kotlin {
                        KotlinSettings::serialization {
                            SerializationSettings::format setTo scalar("json")
                        }
                    }
                }
            }
        }

        val yaml = tree.serializeToYaml()
        assertEquals("""
            product: jvm/app
            
            settings:
              kotlin:
                serialization: json

        """.trimIndent(), yaml)
    }

    @Test
    fun `serialization shorthand example if there are 2 shorthand values`() {
        val tree = syntheticBuilder(SchemaTypingContext(emptyList(), emptyList()), DefaultTrace) {
            `object`<Module> {
                Module::product {
                    ModuleProduct::type setTo scalar(ProductType.JVM_APP)
                }

                Module::settings {
                    Settings::kotlin {
                        KotlinSettings::serialization {
                            SerializationSettings::enabled setTo scalar(true)
                            SerializationSettings::format setTo scalar("json")
                        }
                    }
                }
            }
        }

        val yaml = tree.serializeToYaml()
        assertEquals("""
            product: jvm/app
            
            settings:
              kotlin:
                serialization:
                  enabled: true
                  format: json

        """.trimIndent(), yaml)
    }

    @Test
    fun `settings and test-settings`() {
        val main = syntheticBuilder(SchemaTypingContext(emptyList(), emptyList()), DefaultTrace) {
            `object`<Module> {
                Module::product {
                    ModuleProduct::type setTo scalar(ProductType.JVM_APP)
                }
                Module::settings {
                    Settings::java {
                        JavaSettings::freeCompilerArgs setTo list(SchemaType.ListType(SchemaType.StringType)) {
                            add(scalar("-parameters"))
                        }
                    }
                    Settings::jvm {
                        JvmSettings::release setTo scalar("17")
                    }
                }
            }
        }

        val test = syntheticBuilder(SchemaTypingContext(emptyList(), emptyList()), DefaultTrace, listOf(TestCtx)) {
            `object`<Module> {
                Module::settings {
                    Settings::jvm {
                        JvmSettings::release setTo scalar("21")
                    }
                }
            }
        }

        val merged = mergeTrees(listOf(main, test))
        val yaml = merged.serializeToYaml()

        assertEquals("""
            product: jvm/app
            
            settings:
              java:
                freeCompilerArgs:
                  - -parameters
              jvm:
                release: 17
            
            test-settings:
              jvm:
                release: 21
            
        """.trimIndent(), yaml)
    }

    @Test
    fun `dependencies and test-dependencies`() {
        val main = syntheticBuilder(SchemaTypingContext(emptyList(), emptyList()), dummyTransformedTrace) {
            `object`<Module> {
                Module::product {
                    ModuleProduct::type setTo scalar(ProductType.JVM_APP)
                }

                Module::dependencies {
                    this += `object`<ExternalMavenDependency> {
                        ExternalMavenDependency::coordinates setTo scalar("org.springframework.boot:spring-boot-starter")
                    }

                    this += `object`<ExternalMavenDependency> {
                        ExternalMavenDependency::coordinates setTo scalar("org.springframework.boot:spring-boot-web")
                        ExternalMavenDependency::scope setTo scalar(DependencyScope.COMPILE_ONLY)
                        ExternalMavenDependency::exported setTo scalar(true)
                    }

                    this += `object`<ExternalMavenDependency> {
                        ExternalMavenDependency::coordinates setTo scalar("org.springframework.boot:spring-boot-devtools")
                        ExternalMavenDependency::scope setTo scalar(DependencyScope.RUNTIME_ONLY)
                    }
                }
            }
        }

        val test = syntheticBuilder(SchemaTypingContext(emptyList(), emptyList()), dummyTransformedTrace, listOf(TestCtx)) {
            `object`<Module> {
                Module::dependencies {
                    this += `object`<ExternalMavenDependency> {
                        ExternalMavenDependency::coordinates setTo scalar("org.springframework.boot:spring-boot-starter-test")
                    }
                }
            }
        }

        val merged = mergeTrees(listOf(main, test))

        val yaml = merged.serializeToYaml()

        assertEquals("""
            product: jvm/app
            
            dependencies:
              - org.springframework.boot:spring-boot-starter
              - org.springframework.boot:spring-boot-web:
                  scope: compile-only
                  exported: true
              - org.springframework.boot:spring-boot-devtools: runtime-only

            test-dependencies:
              - org.springframework.boot:spring-boot-starter-test

        """.trimIndent(), yaml)
    }

    @Test
    fun `bom test`() {

        val tree = syntheticBuilder(SchemaTypingContext(emptyList(), emptyList()), DefaultTrace) {
            `object`<Module> {
                Module::product {
                    ModuleProduct::type setTo scalar(ProductType.JVM_APP)
                }

                Module::dependencies {
                    this += `object`<ExternalMavenBomDependency> {
                        ExternalMavenBomDependency::coordinates setTo scalar("org.springframework.boot:spring-boot-dependencies:3.5.7")
                    }
                }
            }
        }

        val yaml = tree.serializeToYaml()

        assertEquals("""
            product: jvm/app
            
            dependencies:
              - bom: org.springframework.boot:spring-boot-dependencies:3.5.7

        """.trimIndent(), yaml)
    }

    @Test
    fun project() {
        val tree = syntheticBuilder(SchemaTypingContext(emptyList(), emptyList()), DefaultTrace) {
            `object`<Project> {
                Project::modules {
                    this += scalar("module1")
                    this += scalar("module2")
                    this += scalar("module3")
                }
            }
        }

        val yaml = tree.serializeToYaml()

        assertEquals("""
            modules:
              - module1
              - module2
              - module3

        """.trimIndent(), yaml)
    }

    @Test
    fun `maven plugins`() {
        val mavenPluginXml = mavenPluginXmlFixture()
        val tree = syntheticBuilder(SchemaTypingContext(emptyList(), listOf(mavenPluginXml)), DefaultTrace) {
            `object`<Module> {
                Module::product {
                    ModuleProduct::type setTo scalar(ProductType.JVM_APP)
                }

                Module::plugins {
                    "maven-surefire-plugin.test" setTo map(SchemaType.MapType(SchemaType.StringType, SchemaType.StringType)) {
                        "enabled" setTo scalar(true)
                        "includes" setTo list(SchemaType.ListType(SchemaType.StringType)) {
                            add(scalar("*First*"))
                        }
                    }
                }
            }
        }

        val yaml = tree.serializeToYaml()

        assertEquals("""
        product: jvm/app
        
        plugins:
          maven-surefire-plugin.test:
            enabled: true
            includes:
              - '*First*'

        """.trimIndent(), yaml)
    }

    private fun mavenPluginXmlFixture(): MavenPluginXml {
        val mavenPluginXml = MavenPluginXml(
            "maven-surefire-plugin",
            "Description",
            "groupId",
            "maven-surefire-plugin",
            "version",
            "",
            false,
            false,
            "17",
            "3",
            Mojos(
                listOf(
                    Mojo(
                        "test",
                        "test",
                        "false",
                        "description",
                        true,
                        false,
                        false,
                        false,
                        false,
                        false,
                        "",
                        "",
                        "",
                        "",
                        true,
                        Parameters(
                            listOf(
                                Parameter("enabled", "Boolean", false, true, ""),
                                Parameter("includes", "String[]", false, true, "")
                            )
                        ),
                        Configuration(emptyList())
                    )
                )
            ),
            Dependencies(emptyList())
        )
        return mavenPluginXml
    }

    @Test
    fun `write the same key several times and refine`() {
        val tree = syntheticBuilder(SchemaTypingContext(emptyList(), emptyList()), dummyTransformedTrace) {
            `object`<Module> {
                Module::dependencies {
                    add(`object`<ExternalMavenBomDependency> {
                        ExternalMavenBomDependency::coordinates setTo scalar("org.example:artifact1:version")
                    })
                }
            }
        }

        val enhancedTree = syntheticBuilder(SchemaTypingContext(emptyList(), emptyList()), dummyTransformedTrace) {
            `object`<Module> {
                Module::dependencies {
                    add(`object`<ExternalMavenBomDependency> {
                        ExternalMavenBomDependency::coordinates setTo scalar("org.example:artifact2:version")
                    })
                }
            }
        }

        val mergedTree = mergeTrees(listOf(tree, enhancedTree))

        val yaml = mergedTree.serializeToYaml()

        assertEquals("""
            dependencies:
              - bom: org.example:artifact1:version
              - bom: org.example:artifact2:version

        """.trimIndent(), yaml)
    }

    @Test
    fun `same option same value for default and test contexts`() {
        val tree = syntheticBuilder(SchemaTypingContext(emptyList(), emptyList()), dummyTransformedTrace) {
            `object`<Module> {
                Module::settings {
                    Settings::kotlin {
                        KotlinSettings::version setTo scalar("1.7.20")
                    }
                }
            }
        }

        val enhancedTree = syntheticBuilder(SchemaTypingContext(emptyList(), emptyList()), dummyTransformedTrace, listOf(TestCtx)) {
            `object`<Module> {
                Module::settings {
                    Settings::kotlin {
                        KotlinSettings::version setTo scalar("1.7.20")
                    }
                }
            }
        }

        val mergedTree = mergeTrees(listOf(tree, enhancedTree))

        val yaml = mergedTree.serializeToYaml()

        assertEquals("""
            settings:
              kotlin:
                version: 1.7.20

        """.trimIndent(), yaml)
    }

    @Test
    fun `same option same value for default and test contexts 1`() {
        val tree = syntheticBuilder(SchemaTypingContext(emptyList(), emptyList()), dummyTransformedTrace) {
            `object`<Module> {
                Module::settings {
                    Settings::kotlin {
                        KotlinSettings::version setTo scalar("1.7.20")
                    }

                    Settings::java {
                        JavaSettings::compileIncrementally setTo scalar(true)
                    }
                }
            }
        }

        val enhancedTree = syntheticBuilder(SchemaTypingContext(emptyList(), emptyList()), dummyTransformedTrace, listOf(TestCtx)) {
            `object`<Module> {
                Module::settings {
                    Settings::kotlin {
                        KotlinSettings::version setTo scalar("1.7.20")
                    }

                    Settings::java {
                        JavaSettings::compileIncrementally setTo scalar(false)
                    }
                }
            }
        }

        val mergedTree = mergeTrees(listOf(tree, enhancedTree))

        val yaml = mergedTree.serializeToYaml()

        assertEquals("""
            settings:
              kotlin:
                version: 1.7.20
              java:
                compileIncrementally: true
            
            test-settings:
              java:
                compileIncrementally: false

        """.trimIndent(), yaml)
    }
}
