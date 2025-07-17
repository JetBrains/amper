/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.processing

import org.jetbrains.amper.core.UsedVersions
import org.jetbrains.amper.frontend.aomBuilder.BuildCtx
import org.jetbrains.amper.frontend.api.DefaultTrace
import org.jetbrains.amper.frontend.schema.JavaAnnotationProcessingSettings
import org.jetbrains.amper.frontend.schema.JavaSettings
import org.jetbrains.amper.frontend.schema.MavenJavaAnnotationProcessorDeclaration
import org.jetbrains.amper.frontend.schema.Module
import org.jetbrains.amper.frontend.schema.Settings
import org.jetbrains.amper.frontend.tree.MapLikeValue
import org.jetbrains.amper.frontend.tree.Merged
import org.jetbrains.amper.frontend.tree.Owned
import org.jetbrains.amper.frontend.tree.TreeValue
import org.jetbrains.amper.frontend.tree.asMapLike
import org.jetbrains.amper.frontend.tree.syntheticBuilder

context(buildCtx: BuildCtx)
internal fun TreeValue<Merged>.configureLombokDefaults(moduleCtxModule: Module) =
    if (moduleCtxModule.settings.lombok.enabled) {
        buildCtx.treeMerger.mergeTrees(listOfNotNull(asMapLike, buildCtx.lombokAnnotationProcessorDefaultsTree()))
    } else {
        this
    }

private fun BuildCtx.lombokAnnotationProcessorDefaultsTree() =
    syntheticBuilder<MapLikeValue<Owned>>(types, DefaultTrace) {
        mapLike<Module> {
            Module::settings {
                Settings::java {
                    JavaSettings::annotationProcessing {
                        JavaAnnotationProcessingSettings::processors.invoke {
                            add(mapLike<MavenJavaAnnotationProcessorDeclaration> {
                                MavenJavaAnnotationProcessorDeclaration::coordinates setTo scalar("org.projectlombok:lombok:${UsedVersions.lombokVersion}")
                            })
                        }
                    }
                }
            }
        }
    }
