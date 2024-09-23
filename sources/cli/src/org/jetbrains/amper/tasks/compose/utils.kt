/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.tasks.compose

import org.jetbrains.amper.frontend.PotatoModule
import org.jetbrains.amper.frontend.schema.commonSettings


internal fun isComposeEnabledFor(module: PotatoModule): Boolean {
    return module.origin.commonSettings.compose.enabled
}

internal fun isComposeResourcesEnabledFor(module: PotatoModule): Boolean {
    // TODO: `!compose.enabled && compose.resources.enabled` => invalid config, report it
    return isComposeEnabledFor(module) && module.origin.commonSettings.compose.resources.enabled
}
