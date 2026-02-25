/*
 * Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.maven

import org.jetbrains.amper.frontend.api.DefaultTrace
import org.jetbrains.amper.frontend.api.TraceableString
import org.jetbrains.amper.frontend.api.TransformedValueTrace
import org.jetbrains.amper.frontend.contexts.TestCtx
import org.jetbrains.amper.frontend.schema.DependencyScope
import org.jetbrains.amper.frontend.schema.ProductType
import org.jetbrains.amper.frontend.tree.add
import org.jetbrains.amper.frontend.tree.buildTree
import org.jetbrains.amper.frontend.tree.invoke
import org.jetbrains.amper.frontend.tree.mergeTrees
import org.jetbrains.amper.frontend.types.SchemaTypingContext
import org.jetbrains.amper.frontend.types.generated.*
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class RenderToYamlTest {

    private val dummyTransformedTrace = TransformedValueTrace(
        "dummy",
        TraceableString("dummy", DefaultTrace),
    )

    private inline fun withTypeContext(
        types: SchemaTypingContext = SchemaTypingContext(),
        block: SchemaTypingContext.() -> Unit,
    ) = types.block()

    @Test
    fun `product without platforms and basic settings`() = withTypeContext {
        val tree = buildTree(moduleDeclaration) {
            product {
                type(ProductType.JVM_APP)
            }
            settings {
                jvm {
                    release(17)
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
    fun `java annotation processors are rendered`() = withTypeContext {
        val tree = buildTree(moduleDeclaration) {
            product {
                type(ProductType.JVM_APP)
            }
            settings {
                java {
                    annotationProcessing {
                        processors {
                            add(DeclarationOfUnscopedExternalMavenDependency) {
                                coordinates("com.example:proc-one:1.0.0")
                            }
                            add(DeclarationOfUnscopedExternalMavenDependency) {
                                coordinates("com.acme:proc-two:2.1.3")
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
    fun `spring boot shorthand example`() = withTypeContext {
        val tree = buildTree(moduleDeclaration) {
            product {
                type(ProductType.JVM_APP)
            }
            settings {
                springBoot {
                    enabled(true)
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
    fun `serialization shorthand example`() = withTypeContext {
        val tree = buildTree(moduleDeclaration) {
            product {
                type(ProductType.JVM_APP)
            }
            settings {
                kotlin {
                    serialization {
                        format("json")
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
    fun `serialization shorthand example if there are 2 shorthand values`() = withTypeContext {
        val tree = buildTree(moduleDeclaration) {
            product {
                type(ProductType.JVM_APP)
            }
            settings {
                kotlin {
                    serialization {
                        enabled(true)
                        format("json")
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
    fun `settings and test-settings`() = withTypeContext {
        val main = buildTree(moduleDeclaration) {
            product {
                type(ProductType.JVM_APP)
            }
            settings {
                java {
                    freeCompilerArgs { add("-parameters") }
                }
                jvm {
                    release(17)
                }
            }
        }

        val test = buildTree(moduleDeclaration, contexts = listOf(TestCtx)) {
            settings {
                jvm {
                    release(21)
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
    fun `dependencies and test-dependencies`() = withTypeContext {
        val main = buildTree(moduleDeclaration, dummyTransformedTrace) {
            product {
                type(ProductType.JVM_APP)
            }
            dependencies {
                add(DeclarationOfExternalMavenDependency) {
                    coordinates("org.springframework.boot:spring-boot-starter")
                }
                add(DeclarationOfExternalMavenDependency) {
                    coordinates("org.springframework.boot:spring-boot-web")
                    scope(DependencyScope.COMPILE_ONLY)
                    exported(true)
                }
                add(DeclarationOfExternalMavenDependency) {
                    coordinates("org.springframework.boot:spring-boot-devtools")
                    scope(DependencyScope.RUNTIME_ONLY)
                }
            }
        }

        val test = buildTree(moduleDeclaration, dummyTransformedTrace, contexts = listOf(TestCtx)) {
            dependencies {
                add(DeclarationOfExternalMavenDependency) {
                    coordinates("org.springframework.boot:spring-boot-starter-test")
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
    fun `bom test`() = withTypeContext {
        val tree = buildTree(moduleDeclaration) {
            product {
                type(ProductType.JVM_APP)
            }
            dependencies {
                add(DeclarationOfExternalMavenBomDependency) {
                    coordinates("org.springframework.boot:spring-boot-dependencies:3.5.7")
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
    fun project() = withTypeContext {
        val tree = buildTree(DeclarationOfProject) {
            modules {
                add("module1")
                add("module2")
                add("module3")
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
    fun `project with maven plugins`() = withTypeContext(SchemaTypingContext(emptyList(), listOf(mavenPluginXmlFixture()))) {
        val tree = buildTree(DeclarationOfProject) {
            modules {
                add("module1")
            }
            mavenPlugins {
                add { coordinates("org.apache.maven.plugins:maven-surefire-plugin:3.5.3") }
                add { coordinates("org.apache.maven.plugins:maven-checkstyle-plugin:3.6.0") }
            }
        }

        val yaml = tree.serializeToYaml()

        assertEquals("""
            modules:
              - module1

            mavenPlugins:
              - org.apache.maven.plugins:maven-surefire-plugin:3.5.3
              - org.apache.maven.plugins:maven-checkstyle-plugin:3.6.0

        """.trimIndent(), yaml)
    }

    @Test
    fun `maven plugins`() = withTypeContext(SchemaTypingContext(emptyList(), listOf(mavenPluginXmlFixture()))) {
        val tree = buildTree(moduleDeclaration) {
            product {
                type(ProductType.JVM_APP)
            }
            plugins(buildRawTree {
                mapping {
                    put("maven-surefire-plugin.test", mapping {
                        put("enabled", scalar("true"))
                        put("includes", list {
                            add(scalar("*First*"))
                        })
                    })
                }
            }, unsafe = true)
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
    fun `write the same key several times and refine`() = withTypeContext {
        val tree = buildTree(moduleDeclaration, dummyTransformedTrace) {
            dependencies {
                add(DeclarationOfExternalMavenBomDependency) {
                    coordinates("org.example:artifact1:version")
                }
            }
        }

        val enhancedTree = buildTree(moduleDeclaration, dummyTransformedTrace) {
            dependencies {
                add(DeclarationOfExternalMavenBomDependency) {
                    coordinates("org.example:artifact2:version")
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
    fun `same option same value for default and test contexts`() = withTypeContext {
        val tree = buildTree(moduleDeclaration) {
            settings {
                kotlin {
                    version("1.7.20")
                }
            }
        }

        val enhancedTree = buildTree(moduleDeclaration, contexts = listOf(TestCtx)) {
            settings {
                kotlin {
                    version("1.7.20")
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
    fun `yaml comments in main section`() = withTypeContext {
        val tree = buildTree(moduleDeclaration) {
            product {
                type(ProductType.JVM_APP)
            }
            settings {
                jvm {
                    release(17)
                }
            }
        }

        val comments = mapOf(
            listOf("settings", "jvm") to YamlComment(
                path = listOf("settings", "jvm"),
                beforeKeyComment = "This is a comment before jvm",
                test = false,
            )
        )

        val yaml = tree.serializeToYaml(comments)
        assertEquals("""
            product: jvm/app

            settings:
              # This is a comment before jvm
              jvm:
                release: 17

        """.trimIndent(), yaml)
    }

    @Test
    fun `yaml comments in test section`() = withTypeContext {
        val main = buildTree(moduleDeclaration) {
            product {
                type(ProductType.JVM_APP)
            }
        }

        val test = buildTree(moduleDeclaration, contexts = listOf(TestCtx)) {
            settings {
                jvm {
                    release(21)
                }
            }
        }

        val merged = mergeTrees(listOf(main, test))

        val comments = mapOf(
            listOf("settings", "jvm") to YamlComment(
                path = listOf("settings", "jvm"),
                beforeKeyComment = "This comment appears only in test",
                test = true,
            )
        )

        val yaml = merged.serializeToYaml(comments)

        assertEquals("""
            product: jvm/app

            test-settings:
              # This comment appears only in test
              jvm:
                release: 21

        """.trimIndent(), yaml)
    }

    @Test
    fun `same option same value for default and test contexts 1`() = withTypeContext {
        val tree = buildTree(moduleDeclaration) {
            settings {
                kotlin {
                    version("1.7.20")
                }
                java {
                    compileIncrementally(true)
                }
            }
        }

        val enhancedTree = buildTree(moduleDeclaration, contexts = listOf(TestCtx)) {
            settings {
                kotlin {
                    version("1.7.20")
                }
                java {
                    compileIncrementally(false)
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
