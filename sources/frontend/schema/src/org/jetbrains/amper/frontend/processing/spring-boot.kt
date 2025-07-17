/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.processing

import org.jetbrains.amper.frontend.aomBuilder.BuildCtx
import org.jetbrains.amper.frontend.api.DefaultTrace
import org.jetbrains.amper.frontend.api.TraceableString
import org.jetbrains.amper.frontend.schema.AllOpenPreset
import org.jetbrains.amper.frontend.schema.AllOpenSettings
import org.jetbrains.amper.frontend.schema.DependencyMode
import org.jetbrains.amper.frontend.schema.JvmSettings
import org.jetbrains.amper.frontend.schema.KotlinSettings
import org.jetbrains.amper.frontend.schema.Module
import org.jetbrains.amper.frontend.schema.NoArgPreset
import org.jetbrains.amper.frontend.schema.NoArgSettings
import org.jetbrains.amper.frontend.schema.Settings
import org.jetbrains.amper.frontend.tree.MapLikeValue
import org.jetbrains.amper.frontend.tree.Merged
import org.jetbrains.amper.frontend.tree.Owned
import org.jetbrains.amper.frontend.tree.TreeValue
import org.jetbrains.amper.frontend.tree.asMapLike
import org.jetbrains.amper.frontend.tree.syntheticBuilder

context(buildCtx: BuildCtx)
internal fun TreeValue<Merged>.configureSpringBootDefaults(moduleCtxModule: Module) =
    if (moduleCtxModule.settings.springBoot.enabled) {
        buildCtx.treeMerger.mergeTrees(listOfNotNull(asMapLike, buildCtx.springBootDefaultsTree()))
    } else {
        this
    }

private fun BuildCtx.springBootDefaultsTree() = syntheticBuilder<MapLikeValue<Owned>>(types, DefaultTrace) {
    mapLike<Module> {
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
                KotlinSettings::freeCompilerArgs { add(scalar(TraceableString("-Xjsr305=strict"))) }
            }
            Settings::jvm {
                JvmSettings::storeParameterNames setTo scalar(true)
                JvmSettings::runtimeClasspathMode setTo scalar(DependencyMode.CLASSES)
            }
        }
    }
}