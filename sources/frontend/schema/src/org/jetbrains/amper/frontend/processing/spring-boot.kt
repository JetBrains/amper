/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.processing

import org.jetbrains.amper.frontend.api.Trace
import org.jetbrains.amper.frontend.api.TransformedValueTrace
import org.jetbrains.amper.frontend.api.schemaDelegate
import org.jetbrains.amper.frontend.schema.AllOpenPreset
import org.jetbrains.amper.frontend.schema.AllOpenSettings
import org.jetbrains.amper.frontend.schema.DependencyMode
import org.jetbrains.amper.frontend.schema.JavaAnnotationProcessingSettings
import org.jetbrains.amper.frontend.schema.JavaSettings
import org.jetbrains.amper.frontend.schema.JvmSettings
import org.jetbrains.amper.frontend.schema.KotlinSettings
import org.jetbrains.amper.frontend.schema.Module
import org.jetbrains.amper.frontend.schema.NoArgPreset
import org.jetbrains.amper.frontend.schema.NoArgSettings
import org.jetbrains.amper.frontend.schema.Settings
import org.jetbrains.amper.frontend.schema.SpringBootSettings
import org.jetbrains.amper.frontend.schema.UnscopedExternalMavenBomDependency
import org.jetbrains.amper.frontend.tree.MappingNode
import org.jetbrains.amper.frontend.tree.mergeTrees
import org.jetbrains.amper.frontend.tree.syntheticBuilder
import org.jetbrains.amper.frontend.types.SchemaTypingContext

context(_: SchemaTypingContext)
internal fun MappingNode.configureSpringBootDefaults(springBootSettings: SpringBootSettings) =
    if (springBootSettings.enabled) {
        val springBootEnabledDefault = TransformedValueTrace(
            description = "because Spring Boot is enabled",
            sourceValue = springBootSettings::enabled.schemaDelegate,
        )

        val springBootApplyBomDefault = TransformedValueTrace(
            description = "because applyBom=true",
            sourceValue = springBootSettings::applyBom.schemaDelegate,
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

context(_: SchemaTypingContext)
private fun springBootDefaultsTree(
    applyBom: Boolean,
    springBootVersion: String,
    springBootEnabledTrace: Trace,
    springBootApplyBomTrace: Trace,
) = syntheticBuilder(springBootEnabledTrace) {
    `object`<Module> {
        Module::settings {
            Settings::kotlin {
                KotlinSettings::allOpen {
                    AllOpenSettings::enabled setTo scalar(true)
                    AllOpenSettings::presets { add(scalar(AllOpenPreset.Spring)) }
                }
                KotlinSettings::noArg {
                    NoArgSettings::enabled setTo scalar(true)
                    NoArgSettings::presets { add(scalar(NoArgPreset.Jpa)) }
                }
                KotlinSettings::freeCompilerArgs { add(traceableScalar("-Xjsr305=strict", springBootEnabledTrace)) }
            }
            if (applyBom) {
                Settings::java {
                    JavaSettings::annotationProcessing {
                        JavaAnnotationProcessingSettings::processors {
                            add(`object`<UnscopedExternalMavenBomDependency> {
                                UnscopedExternalMavenBomDependency::coordinates setTo scalar(
                                    "org.springframework.boot:spring-boot-dependencies:$springBootVersion",
                                    springBootApplyBomTrace,
                                )
                            })
                        }
                    }
                }
            }
            Settings::jvm {
                JvmSettings::storeParameterNames setTo scalar(true)
                JvmSettings::runtimeClasspathMode setTo scalar(DependencyMode.CLASSES)
            }
        }
    }
}