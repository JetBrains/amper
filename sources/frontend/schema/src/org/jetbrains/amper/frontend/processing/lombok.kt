/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.processing

import org.jetbrains.amper.frontend.api.Trace
import org.jetbrains.amper.frontend.api.TransformedValueTrace
import org.jetbrains.amper.frontend.schema.LombokSettings
import org.jetbrains.amper.frontend.tree.MappingNode
import org.jetbrains.amper.frontend.tree.add
import org.jetbrains.amper.frontend.tree.buildTree
import org.jetbrains.amper.frontend.tree.invoke
import org.jetbrains.amper.frontend.tree.mergeTrees
import org.jetbrains.amper.frontend.tree.withTrace
import org.jetbrains.amper.frontend.types.SchemaTypingContext
import org.jetbrains.amper.frontend.types.generated.*

context(_: SchemaTypingContext)
internal fun MappingNode.configureLombokDefaults(lombokSettings: LombokSettings): MappingNode {
    return if (lombokSettings.enabled) {
        val lombokDefault = TransformedValueTrace(
            description = "because Lombok is enabled",
            sourceValue = lombokSettings.enabledDelegate,
        )
        val elements = lombokAnnotationProcessorDefaultsTree(
            trace = lombokDefault,
            lombokVersion = lombokSettings.version,
            versionTrace = lombokSettings.versionDelegate.trace,
        )
        mergeTrees(this, elements)
    } else {
        this
    }
}

context(types: SchemaTypingContext)
private fun lombokAnnotationProcessorDefaultsTree(trace: Trace, lombokVersion: String, versionTrace: Trace) =
    buildTree(types.moduleDeclaration, trace) {
        settings {
            java {
                annotationProcessing {
                    processors {
                        add(DeclarationOfUnscopedExternalMavenDependency) {
                            withTrace(versionTrace) {
                                coordinates("org.projectlombok:lombok:$lombokVersion")
                            }
                        }
                    }
                }
            }
        }
    }
