/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.processing

import org.jetbrains.amper.frontend.api.Trace
import org.jetbrains.amper.frontend.api.TransformedValueTrace
import org.jetbrains.amper.frontend.schema.AllOpenPreset
import org.jetbrains.amper.frontend.schema.DependencyMode
import org.jetbrains.amper.frontend.schema.NoArgPreset
import org.jetbrains.amper.frontend.schema.SpringBootSettings
import org.jetbrains.amper.frontend.tree.MappingNode
import org.jetbrains.amper.frontend.tree.add
import org.jetbrains.amper.frontend.tree.buildTree
import org.jetbrains.amper.frontend.tree.invoke
import org.jetbrains.amper.frontend.tree.mergeTrees
import org.jetbrains.amper.frontend.tree.withTrace
import org.jetbrains.amper.frontend.types.SchemaTypingContext
import org.jetbrains.amper.frontend.types.generated.*

context(_: SchemaTypingContext)
internal fun MappingNode.configureSpringBootDefaults(springBootSettings: SpringBootSettings) =
    if (springBootSettings.enabled) {
        val springBootEnabledDefault = TransformedValueTrace(
            description = "because Spring Boot is enabled",
            sourceValue = springBootSettings.enabledDelegate,
        )

        val springBootApplyBomDefault = TransformedValueTrace(
            description = "because applyBom=true",
            sourceValue = springBootSettings.applyBomDelegate,
        )
        val applyBom = springBootSettings.applyBom
        val springBootVersion = springBootSettings.version
        mergeTrees(
            this,
            springBootDefaultsTree(
                applyBom,
                springBootVersion,
                springBootEnabledDefault,
                springBootApplyBomDefault,
            ),
        )
    } else {
        this
    }

context(types: SchemaTypingContext)
private fun springBootDefaultsTree(
    applyBom: Boolean,
    springBootVersion: String,
    springBootEnabledTrace: Trace,
    springBootApplyBomTrace: Trace,
) = buildTree(types.moduleDeclaration, trace = springBootEnabledTrace) {
    settings {
        kotlin {
            allOpen {
                enabled(true)
                presets { add(AllOpenPreset.Spring) }
            }
            noArg {
                enabled(true)
                presets { add(NoArgPreset.Jpa) }
            }
            freeCompilerArgs { withTrace(springBootEnabledTrace) { add("-Xjsr305=strict") } }
        }
        if (applyBom) {
            java {
                annotationProcessing {
                    processors {
                        add(DeclarationOfUnscopedExternalMavenBomDependency) {
                            withTrace(springBootApplyBomTrace) {
                                coordinates("org.springframework.boot:spring-boot-dependencies:$springBootVersion")
                            }
                        }
                    }
                }
            }
        }
        jvm {
            storeParameterNames(true)
            runtimeClasspathMode(DependencyMode.CLASSES)
        }
    }
}