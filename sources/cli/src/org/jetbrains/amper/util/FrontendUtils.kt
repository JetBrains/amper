/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.util

import org.jetbrains.amper.frontend.LeafFragment
import org.jetbrains.amper.frontend.AmperModule

val AmperModule.targetLeafPlatforms
    get() = fragments.filterIsInstance<LeafFragment>().flatMap { it.platforms }
        .filter { it.isLeaf }
        .toSet()
