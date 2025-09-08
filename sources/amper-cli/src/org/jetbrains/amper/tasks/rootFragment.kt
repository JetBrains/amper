/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.tasks

import org.jetbrains.amper.frontend.AmperModule
import org.jetbrains.amper.frontend.Fragment

val AmperModule.rootFragment: Fragment get() = fragments.first { it.fragmentDependencies.isEmpty() }