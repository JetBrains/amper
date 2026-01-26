/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.processing

import org.jetbrains.amper.frontend.api.Trace
import org.jetbrains.amper.frontend.api.TransformedValueTrace
import org.jetbrains.amper.frontend.api.schemaDelegate
import org.jetbrains.amper.frontend.schema.JavaAnnotationProcessingSettings
import org.jetbrains.amper.frontend.schema.JavaSettings
import org.jetbrains.amper.frontend.schema.LombokSettings
import org.jetbrains.amper.frontend.schema.Module
import org.jetbrains.amper.frontend.schema.Settings
import org.jetbrains.amper.frontend.schema.UnscopedExternalMavenDependency
import org.jetbrains.amper.frontend.tree.MappingNode
import org.jetbrains.amper.frontend.tree.mergeTrees
import org.jetbrains.amper.frontend.tree.syntheticBuilder
import org.jetbrains.amper.frontend.types.SchemaTypingContext

context(_: SchemaTypingContext)
internal fun MappingNode.configureLombokDefaults(lombokSettings: LombokSettings): MappingNode {
    return if (lombokSettings.enabled) {
        val lombokDefault = TransformedValueTrace(
            description = "because Lombok is enabled",
            sourceValue = lombokSettings::enabled.schemaDelegate,
        )
        val elements = lombokAnnotationProcessorDefaultsTree(
            trace = lombokDefault,
            lombokVersion = lombokSettings.version,
            versionTrace = lombokSettings::version.schemaDelegate.trace,
        )
        mergeTrees(this, elements)
    } else {
        this
    }
}

context(_: SchemaTypingContext)
private fun lombokAnnotationProcessorDefaultsTree(trace: Trace, lombokVersion: String, versionTrace: Trace) =
    syntheticBuilder(trace) {
        `object`<Module> {
            Module::settings {
                Settings::java {
                    JavaSettings::annotationProcessing {
                        JavaAnnotationProcessingSettings::processors {
                            this += `object`<UnscopedExternalMavenDependency> {
                                UnscopedExternalMavenDependency::coordinates setTo scalar("org.projectlombok:lombok:$lombokVersion", versionTrace)
                            }
                        }
                    }
                }
            }
        }
    }
