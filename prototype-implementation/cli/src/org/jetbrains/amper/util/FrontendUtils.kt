/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.util

import org.jetbrains.amper.frontend.PotatoModule

// TODO dunno how to get it legally
val PotatoModule.targetLeafPlatforms
    get() = (fragments.flatMap { it.platforms } + artifacts.flatMap { it.platforms })
        .filter { it.isLeaf }
        .toSet()
