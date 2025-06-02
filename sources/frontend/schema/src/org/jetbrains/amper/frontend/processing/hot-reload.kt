/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.processing

import org.jetbrains.amper.frontend.schema.DependencyMode
import org.jetbrains.amper.frontend.schema.Module

fun Module.configureHotReloadDefaults() = apply {
    settings.values.forEach { fragmentSettings ->
        if (fragmentSettings.compose.enabled && fragmentSettings.compose.experimental.hotReload.enabled) {
            if (fragmentSettings.jvm.trace == null) {
                fragmentSettings.jvm.runtimeClasspathMode = DependencyMode.CLASSES
            }
        }
    }
}
