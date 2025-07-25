/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.processing

import org.jetbrains.amper.core.UsedVersions
import org.jetbrains.amper.frontend.aomBuilder.BuildCtx
import org.jetbrains.amper.frontend.api.DefaultTrace
import org.jetbrains.amper.frontend.api.Trace
import org.jetbrains.amper.frontend.api.valueBase
import org.jetbrains.amper.frontend.schema.JavaAnnotationProcessingSettings
import org.jetbrains.amper.frontend.schema.JavaSettings
import org.jetbrains.amper.frontend.schema.MavenJavaAnnotationProcessorDeclaration
import org.jetbrains.amper.frontend.schema.Module
import org.jetbrains.amper.frontend.schema.Settings
import org.jetbrains.amper.frontend.tree.Merged
import org.jetbrains.amper.frontend.tree.asMapLike
import org.jetbrains.amper.frontend.tree.syntheticBuilder

context(buildCtx: BuildCtx)
internal fun Merged.configureLombokDefaults(moduleCtxModule: Module): Merged =
    if (moduleCtxModule.settings.lombok.enabled) {
        val lombokDefault = DefaultTrace(computedValueTrace = moduleCtxModule.settings.lombok::enabled.valueBase)
        buildCtx.treeMerger.mergeTrees(listOfNotNull(asMapLike, buildCtx.lombokAnnotationProcessorDefaultsTree(trace = lombokDefault)))
   } else {
        this
    }

private fun BuildCtx.lombokAnnotationProcessorDefaultsTree(trace: Trace) =
    syntheticBuilder(types, trace) {
        `object`<Module> {
            Module::settings {
                Settings::java {
                    JavaSettings::annotationProcessing {
                        JavaAnnotationProcessingSettings::processors {
                            this += `object`<MavenJavaAnnotationProcessorDeclaration> {
                                MavenJavaAnnotationProcessorDeclaration::coordinates setTo scalar("org.projectlombok:lombok:${UsedVersions.lombokVersion}")
                            }
                        }
                    }
                }
            }
        }
    }
