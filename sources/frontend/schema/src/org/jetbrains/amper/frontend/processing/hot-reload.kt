/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.processing

import org.jetbrains.amper.frontend.aomBuilder.BuildCtx
import org.jetbrains.amper.frontend.api.DefaultTrace
import org.jetbrains.amper.frontend.schema.DependencyMode
import org.jetbrains.amper.frontend.schema.JvmSettings
import org.jetbrains.amper.frontend.schema.Module
import org.jetbrains.amper.frontend.schema.Settings
import org.jetbrains.amper.frontend.tree.MapLikeValue
import org.jetbrains.amper.frontend.tree.Merged
import org.jetbrains.amper.frontend.tree.Owned
import org.jetbrains.amper.frontend.tree.TreeValue
import org.jetbrains.amper.frontend.tree.asMapLike
import org.jetbrains.amper.frontend.tree.asOwned
import org.jetbrains.amper.frontend.tree.syntheticBuilder


context(BuildCtx)
internal fun TreeValue<Merged>.configureHotReloadDefaults(commonModule: Module) =
    if (commonModule.settings.compose.enabled && commonModule.settings.compose.experimental.hotReload.enabled)
        treeMerger.mergeTrees(
            listOfNotNull(asOwned().asMapLike, hotReloadDefaulsTree())
        ) else this

private fun BuildCtx.hotReloadDefaulsTree() = syntheticBuilder<MapLikeValue<Owned>>(types, DefaultTrace) {
    mapLike<Module> {
        Module::settings {
            Settings::jvm {
                JvmSettings::runtimeClasspathMode setTo scalar(DependencyMode.CLASSES)
            }
        }
    }
}
