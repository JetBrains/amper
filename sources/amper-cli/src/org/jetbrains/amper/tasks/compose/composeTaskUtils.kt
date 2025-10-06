/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.tasks.compose

import org.jetbrains.amper.frontend.AmperModule
import org.jetbrains.amper.tasks.rootFragment

private val AmperModule.commonSettings get() = rootFragment.settings

// TODO Fix that with new frontend!
internal fun isComposeEnabledFor(module: AmperModule) =
    module.commonSettings.compose.enabled
