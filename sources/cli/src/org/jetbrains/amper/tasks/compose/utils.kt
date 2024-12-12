/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.tasks.compose

import org.jetbrains.amper.frontend.AmperModule
import org.jetbrains.amper.frontend.schema.commonSettings


internal fun isComposeEnabledFor(module: AmperModule): Boolean = module.origin.commonSettings.compose.enabled

internal fun isHotReloadEnabledFor(module: AmperModule): Boolean = module
    .origin
    .commonSettings
    .compose
    .experimental
    .hotReload
    .enabled
