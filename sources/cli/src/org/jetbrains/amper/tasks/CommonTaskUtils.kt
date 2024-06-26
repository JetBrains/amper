/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.tasks

import org.jetbrains.amper.frontend.Fragment

object CommonTaskUtils {
    fun Iterable<Fragment>.userReadableList() = map { it.name }.sorted().joinToString(" ")
}
