/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.processing

import org.jetbrains.amper.frontend.aomBuilder.BuildCtx
import org.jetbrains.amper.frontend.api.DefaultTrace
import org.jetbrains.amper.frontend.api.Trace
import org.jetbrains.amper.frontend.api.valueBase
import org.jetbrains.amper.frontend.schema.DependencyMode
import org.jetbrains.amper.frontend.schema.JvmSettings
import org.jetbrains.amper.frontend.schema.Module
import org.jetbrains.amper.frontend.schema.Settings
import org.jetbrains.amper.frontend.tree.Merged
import org.jetbrains.amper.frontend.tree.asMapLike
import org.jetbrains.amper.frontend.tree.syntheticBuilder

context(buildCtx: BuildCtx)
internal fun Merged.configureHotReloadDefaults(commonModule: Module) =
    if (commonModule.settings.compose.enabled && commonModule.settings.compose.experimental.hotReload.enabled) {
        val hotReloadDefault = DefaultTrace(computedValueTrace = commonModule.settings.compose.experimental.hotReload::enabled.valueBase)
        buildCtx.treeMerger.mergeTrees(listOfNotNull(asMapLike, buildCtx.hotReloadDefaultTree(trace = hotReloadDefault)))
    } else {
        this
    }

private fun BuildCtx.hotReloadDefaultTree(trace: Trace) = syntheticBuilder(types, trace) {
    `object`<Module> {
        Module::settings {
            Settings::jvm {
                JvmSettings::runtimeClasspathMode setTo scalar(DependencyMode.CLASSES)
            }
        }
    }
}
