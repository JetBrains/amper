/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.core

fun <T> Collection<T>.forEachEndAware(block: (Boolean, T) -> Unit) =
    forEachIndexed { index, it -> if (index == size - 1) block(true, it) else block(false, it) }
