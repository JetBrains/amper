/*
 * Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend

fun <T> Collection<T>.forEachEndAware(block: (Boolean, T) -> Unit) =
    forEachIndexed { index, it -> if (index == size - 1) block(true, it) else block(false, it) }

fun <T, V> Collection<T>.mapStartAware(block: (Boolean, T) -> V) =
    mapIndexed { index, it -> if (index == 0) block(true, it) else block(false, it) }