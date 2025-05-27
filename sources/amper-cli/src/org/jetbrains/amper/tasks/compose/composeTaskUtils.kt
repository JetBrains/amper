/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.tasks.compose

import org.jetbrains.amper.frontend.AmperModule


private val AmperModule.commonSettings get() = rootFragment.settings 

// TODO Fix that with new frontend!
internal fun isComposeEnabledFor(module: AmperModule) =
    module.commonSettings.compose.enabled

// TODO Fix that with new frontend!
internal fun isHotReloadEnabledFor(module: AmperModule) =
    module.commonSettings.compose.experimental.hotReload.enabled
